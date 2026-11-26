// Bridge between Film Desk and LibRaw: develops a RAW file (Fujifilm RAF, DNG, CR2/CR3, NEF,
// ARW, ORF, RW2, ...) into an sRGB Android Bitmap that the editor then treats like any photo.
#include <jni.h>
#include <android/bitmap.h>
#include <android/log.h>
#include <cstdint>
#include <cmath>
#include <cstring>
#include <vector>
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
    // No auto-brightening: it clips the brightest 1% to white and throws away the highlight
    // detail RAW is kept for. Brightness comes from the camera's own exposure data instead
    // (see nativeCopy).
    o.no_auto_bright = 1;
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

// Size of the developed image, already turned upright: [width, height].
JNIEXPORT jintArray JNICALL
Java_com_filmdesk_app_engine_RawDecoder_nativeSize(JNIEnv *env, jclass, jlong h) {
    const auto &sz = handle(h)->imgdata.sizes;
    int w = sz.width, ht = sz.height;
    if (sz.flip & 4) { int t = w; w = ht; ht = t; }
    jint out[2] = {w, ht};
    jintArray arr = env->NewIntArray(2);
    env->SetIntArrayRegion(arr, 0, 2, out);
    return arr;
}

namespace {

/**
 * How much to brighten the linear RAW, in EV, from the camera's own data. Cameras store how far
 * the RAW sits under the JPEG: Fujifilm writes it per shot (more for DR200 / DR400, which
 * underexpose on purpose), Canon's Highlight Tone Priority and some Nikon modes too; DNG files
 * carry BaselineExposure.
 */
float metadataEv(const LibRaw *raw) {
    float ev = -raw->imgdata.makernotes.common.ExposureCalibrationShift;
    float dng = raw->imgdata.color.dng_levels.baseline_exposure;
    if (dng > -10.f && dng < 10.f) ev += dng;
    if (!(ev > -1.f)) ev = -1.f;   // also catches NaN
    if (ev > 3.f) ev = 3.f;
    return ev;
}

/**
 * Brightness gain for the photo: at least what the camera's data asks for, and for a dark
 * photo enough to bring its bright end (99th percentile) near white, like the camera's JPEG,
 * up to 3 EV. Nothing is clipped: the shoulder in toneTable rolls the top off smoothly.
 */
double photoGain(const LibRaw *raw) {
    const auto &sz = raw->imgdata.sizes;
    const size_t n = (size_t) sz.width * sz.height;
    auto image = raw->imgdata.image;
    std::vector<unsigned> hist(4096, 0);
    for (size_t i = 0; i < n; i += 3) {   // every 3rd pixel is plenty
        unsigned m = image[i][0];
        if (image[i][1] > m) m = image[i][1];
        if (image[i][2] > m) m = image[i][2];
        hist[m >> 4]++;
    }
    size_t total = (n + 2) / 3, limit = total / 100, acc = 0;
    int p99 = 4095;
    for (; p99 > 0; p99--) { acc += hist[p99]; if (acc > limit) break; }
    double bright = (p99 + 0.5) / 4096.0;
    double autoGain = 0.9 / (bright > 1e-4 ? bright : 1e-4);
    if (autoGain < 1.0) autoGain = 1.0;
    if (autoGain > 8.0) autoGain = 8.0;
    double meta = std::pow(2.0, metadataEv(raw));
    return meta > autoGain ? meta : autoGain;
}

/**
 * Linear 16-bit RAW value to an 8-bit sRGB value with a camera-JPEG-like look:
 * exposure (photoGain) -> a soft highlight shoulder, so brightened highlights roll
 * off instead of clipping -> sRGB gamma -> a gentle S-curve like the contrast cameras add.
 */
std::vector<uint8_t> toneTable(double gain) {
    std::vector<uint8_t> lut(65536);
    const double knee = 0.8;
    const double top = gain;                       // brightest possible input after gain
    const double a = 1.0 / (1.0 - knee);
    const double norm = top > knee ? 1.0 - std::exp(-a * (top - knee)) : 1.0;
    const double contrast = 0.12;
    for (int i = 0; i < 65536; i++) {
        double x = i / 65535.0 * gain;
        if (x > knee) x = knee + (1.0 - knee) * (1.0 - std::exp(-a * (x - knee))) / norm;
        if (x > 1.0) x = 1.0;
        double y = x <= 0.0031308 ? 12.92 * x : 1.055 * std::pow(x, 1.0 / 2.4) - 0.055;
        y = y - contrast * std::sin(2.0 * M_PI * y) / (2.0 * M_PI);
        int v = (int) std::lround(y * 255.0);
        lut[i] = (uint8_t) (v < 0 ? 0 : v > 255 ? 255 : v);
    }
    return lut;
}

}  // namespace

// Writes the developed image straight into [bitmap] (ARGB_8888, size from nativeSize): no
// extra full-size copy, which keeps memory down for 40-100 MP files.
JNIEXPORT jboolean JNICALL
Java_com_filmdesk_app_engine_RawDecoder_nativeCopy(JNIEnv *env, jclass, jlong h, jobject bitmap) {
    LibRaw *raw = handle(h);
    const auto &sz = raw->imgdata.sizes;
    auto image = raw->imgdata.image;
    if (!image || raw->imgdata.idata.colors < 3) return JNI_FALSE;
    const int iw = sz.width, ih = sz.height, flip = sz.flip;
    const int ow = (flip & 4) ? ih : iw, oh = (flip & 4) ? iw : ih;

    AndroidBitmapInfo info;
    void *pixels = nullptr;
    if (AndroidBitmap_getInfo(env, bitmap, &info) != ANDROID_BITMAP_RESULT_SUCCESS ||
        info.format != ANDROID_BITMAP_FORMAT_RGBA_8888 || (int) info.width != ow || (int) info.height != oh ||
        AndroidBitmap_lockPixels(env, bitmap, &pixels) != ANDROID_BITMAP_RESULT_SUCCESS) {
        return JNI_FALSE;
    }
    std::vector<uint8_t> lut = toneTable(photoGain(raw));
    for (int y = 0; y < oh; y++) {
        auto *dst = reinterpret_cast<uint8_t *>(pixels) + (size_t) y * info.stride;
        for (int x = 0; x < ow; x++) {
            // Same orientation rule as LibRaw's own output (flip_index).
            int row = y, col = x;
            if (flip & 4) { int t = row; row = col; col = t; }
            if (flip & 2) row = ih - 1 - row;
            if (flip & 1) col = iw - 1 - col;
            const ushort *p = image[(size_t) row * iw + col];
            dst[0] = lut[p[0]];
            dst[1] = lut[p[1]];
            dst[2] = lut[p[2]];
            dst[3] = 255;
            dst += 4;
        }
    }
    AndroidBitmap_unlockPixels(env, bitmap);
    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_com_filmdesk_app_engine_RawDecoder_nativeClose(JNIEnv *, jclass, jlong h) {
    delete handle(h);
}

}  // extern "C"
