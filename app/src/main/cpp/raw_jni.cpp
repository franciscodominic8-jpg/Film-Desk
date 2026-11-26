// Bridge between Film Desk and LibRaw: develops a RAW file (Fujifilm RAF, DNG, CR2/CR3, NEF,
// ARW, ORF, RW2, ...) into an sRGB Android Bitmap that the editor then treats like any photo.
#include <jni.h>
#include <android/bitmap.h>
#include <android/log.h>
#include <cstdint>
#include <cstring>
#include "libraw/libraw.h"

#define TAG "FilmDeskRaw"

namespace {

LibRaw *handle(jlong h) { return reinterpret_cast<LibRaw *>(h); }

// Opens [path] with the settings Film Desk always uses. Returns nullptr on failure.
LibRaw *openRaw(JNIEnv *env, jstring jpath) {
    const char *path = env->GetStringUTFChars(jpath, nullptr);
    auto *raw = new LibRaw();
    int r = raw->open_file(path);
    env->ReleaseStringUTFChars(jpath, path);
    if (r != LIBRAW_SUCCESS) {
        __android_log_print(ANDROID_LOG_WARN, TAG, "open failed: %s", libraw_strerror(r));
        delete raw;
        return nullptr;
    }
    return raw;
}

}  // namespace

extern "C" {

// Width, height and orientation flag of the photo as it will come out (after turning), or null.
JNIEXPORT jintArray JNICALL
Java_com_filmdesk_app_engine_RawDecoder_nativeInfo(JNIEnv *env, jclass, jstring jpath) {
    LibRaw *raw = openRaw(env, jpath);
    if (!raw) return nullptr;
    raw->adjust_sizes_info_only();  // output size, already turned for the photo's orientation
    int w = raw->imgdata.sizes.iwidth, h = raw->imgdata.sizes.iheight;
    int flip = raw->imgdata.sizes.flip;
    delete raw;
    jint out[3] = {w, h, flip};
    jintArray arr = env->NewIntArray(3);
    env->SetIntArrayRegion(arr, 0, 3, out);
    return arr;
}

// Decodes and develops the RAW. [half] = half-size develop (fast, for previews and thumbnails).
// [highlight]: 0 clip, 1 unclip, 2 blend (gentle recovery). Returns a handle (0 = failed).
JNIEXPORT jlong JNICALL
Java_com_filmdesk_app_engine_RawDecoder_nativeDevelop(JNIEnv *env, jclass, jstring jpath, jboolean half, jint highlight) {
    LibRaw *raw = openRaw(env, jpath);
    if (!raw) return 0;
    auto &o = raw->imgdata.params;
    o.use_camera_wb = 1;          // white balance as shot; Film Desk's own WB works on top
    o.use_camera_matrix = 1;
    o.output_color = 1;           // sRGB
    o.output_bps = 8;
    o.gamm[0] = 1.0 / 2.4;        // sRGB tone curve, so the editor sees a normal photo
    o.gamm[1] = 12.92;
    o.half_size = half ? 1 : 0;
    o.highlight = highlight;
    // Full size: AHD for Bayer sensors; 1-pass Markesteijn for Fujifilm X-Trans (3 passes
    // look almost the same and take about three times as long on a phone).
    o.user_qual = half ? 0 : (raw->imgdata.idata.filters == 9 ? 2 : 3);
    int r = raw->unpack();
    if (r == LIBRAW_SUCCESS) r = raw->dcraw_process();
    if (r != LIBRAW_SUCCESS) {
        __android_log_print(ANDROID_LOG_WARN, TAG, "develop failed: %s", libraw_strerror(r));
        delete raw;
        return 0;
    }
    return reinterpret_cast<jlong>(raw);
}

// Size of the developed image: [width, height].
JNIEXPORT jintArray JNICALL
Java_com_filmdesk_app_engine_RawDecoder_nativeSize(JNIEnv *env, jclass, jlong h) {
    int w = 0, ht = 0, colors = 0, bps = 0;
    handle(h)->get_mem_image_format(&w, &ht, &colors, &bps);
    jint out[2] = {w, ht};
    jintArray arr = env->NewIntArray(2);
    env->SetIntArrayRegion(arr, 0, 2, out);
    return arr;
}

// Copies the developed image into [bitmap] (ARGB_8888, same size). Frees LibRaw's buffers first.
JNIEXPORT jboolean JNICALL
Java_com_filmdesk_app_engine_RawDecoder_nativeCopy(JNIEnv *env, jclass, jlong h, jobject bitmap) {
    LibRaw *raw = handle(h);
    int err = 0;
    libraw_processed_image_t *img = raw->dcraw_make_mem_image(&err);
    if (!img) return JNI_FALSE;
    raw->recycle();  // the full-size work buffers are no longer needed
    bool ok = false;
    AndroidBitmapInfo info;
    void *pixels = nullptr;
    if (AndroidBitmap_getInfo(env, bitmap, &info) == ANDROID_BITMAP_RESULT_SUCCESS &&
        info.format == ANDROID_BITMAP_FORMAT_RGBA_8888 &&
        (int) info.width == img->width && (int) info.height == img->height &&
        img->colors == 3 && img->bits == 8 &&
        AndroidBitmap_lockPixels(env, bitmap, &pixels) == ANDROID_BITMAP_RESULT_SUCCESS) {
        const uint8_t *src = img->data;
        for (uint32_t y = 0; y < info.height; y++) {
            auto *dst = reinterpret_cast<uint8_t *>(pixels) + (size_t) y * info.stride;
            for (uint32_t x = 0; x < info.width; x++) {
                dst[0] = src[0];
                dst[1] = src[1];
                dst[2] = src[2];
                dst[3] = 255;
                dst += 4;
                src += 3;
            }
        }
        AndroidBitmap_unlockPixels(env, bitmap);
        ok = true;
    }
    LibRaw::dcraw_clear_mem(img);
    return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_filmdesk_app_engine_RawDecoder_nativeClose(JNIEnv *, jclass, jlong h) {
    delete handle(h);
}

}  // extern "C"
