# RAW support: LibRaw (used under its CDDL 1.0 licence, see LibRaw/LICENSE.CDDL) and a small
# JNI bridge that develops a RAW file straight into an Android Bitmap.
cmake_minimum_required(VERSION 3.22.1)
project(filmdeskraw CXX)

set(CMAKE_CXX_STANDARD 11)

add_library(raw_r SHARED
    LibRaw/src/libraw_c_api.cpp
    LibRaw/src/libraw_datastream.cpp
    LibRaw/src/decoders/canon_600.cpp
    LibRaw/src/decoders/crx.cpp
    LibRaw/src/decoders/pana8.cpp
    LibRaw/src/decoders/decoders_dcraw.cpp
    LibRaw/src/decoders/sonycc.cpp
    LibRaw/src/decompressors/losslessjpeg.cpp
    LibRaw/src/decoders/decoders_libraw_dcrdefs.cpp
    LibRaw/src/decoders/olympus14.cpp
    LibRaw/src/decoders/decoders_libraw.cpp
    LibRaw/src/decoders/dng.cpp
    LibRaw/src/decoders/fp_dng.cpp
    LibRaw/src/decoders/fuji_compressed.cpp
    LibRaw/src/decoders/generic.cpp
    LibRaw/src/decoders/kodak_decoders.cpp
    LibRaw/src/decoders/load_mfbacks.cpp
    LibRaw/src/decoders/smal.cpp
    LibRaw/src/decoders/unpack_thumb.cpp
    LibRaw/src/decoders/unpack.cpp
    LibRaw/src/demosaic/aahd_demosaic.cpp
    LibRaw/src/demosaic/ahd_demosaic.cpp
    LibRaw/src/demosaic/dcb_demosaic.cpp
    LibRaw/src/demosaic/dht_demosaic.cpp
    LibRaw/src/demosaic/misc_demosaic.cpp
    LibRaw/src/demosaic/xtrans_demosaic.cpp
    LibRaw/src/integration/dngsdk_glue.cpp
    LibRaw/src/integration/rawspeed_glue.cpp
    LibRaw/src/metadata/adobepano.cpp
    LibRaw/src/metadata/canon.cpp
    LibRaw/src/metadata/ciff.cpp
    LibRaw/src/metadata/cr3_parser.cpp
    LibRaw/src/metadata/epson.cpp
    LibRaw/src/metadata/exif_gps.cpp
    LibRaw/src/metadata/fuji.cpp
    LibRaw/src/metadata/identify_tools.cpp
    LibRaw/src/metadata/identify.cpp
    LibRaw/src/metadata/kodak.cpp
    LibRaw/src/metadata/leica.cpp
    LibRaw/src/metadata/makernotes.cpp
    LibRaw/src/metadata/mediumformat.cpp
    LibRaw/src/metadata/minolta.cpp
    LibRaw/src/metadata/misc_parsers.cpp
    LibRaw/src/metadata/nikon.cpp
    LibRaw/src/metadata/normalize_model.cpp
    LibRaw/src/metadata/olympus.cpp
    LibRaw/src/metadata/hasselblad_model.cpp
    LibRaw/src/metadata/p1.cpp
    LibRaw/src/metadata/pentax.cpp
    LibRaw/src/metadata/samsung.cpp
    LibRaw/src/metadata/sony.cpp
    LibRaw/src/metadata/tiff.cpp
    LibRaw/src/postprocessing/aspect_ratio.cpp
    LibRaw/src/postprocessing/dcraw_process.cpp
    LibRaw/src/postprocessing/mem_image.cpp
    LibRaw/src/postprocessing/postprocessing_aux.cpp
    LibRaw/src/postprocessing/postprocessing_utils_dcrdefs.cpp
    LibRaw/src/postprocessing/postprocessing_utils.cpp
    LibRaw/src/preprocessing/ext_preprocess.cpp
    LibRaw/src/preprocessing/raw2image.cpp
    LibRaw/src/preprocessing/subtract_black.cpp
    LibRaw/src/tables/cameralist.cpp
    LibRaw/src/tables/colorconst.cpp
    LibRaw/src/tables/colordata.cpp
    LibRaw/src/tables/wblists.cpp
    LibRaw/src/utils/curves.cpp
    LibRaw/src/utils/decoder_info.cpp
    LibRaw/src/utils/init_close_utils.cpp
    LibRaw/src/utils/open.cpp
    LibRaw/src/utils/phaseone_processing.cpp
    LibRaw/src/utils/read_utils.cpp
    LibRaw/src/utils/thumb_utils.cpp
    LibRaw/src/utils/utils_dcraw.cpp
    LibRaw/src/utils/utils_libraw.cpp
    LibRaw/src/write/apply_profile.cpp
    LibRaw/src/write/file_write.cpp
    LibRaw/src/write/tiff_writer.cpp
    LibRaw/src/x3f/x3f_parse_process.cpp
    LibRaw/src/x3f/x3f_utils_patched.cpp
)
target_include_directories(raw_r PUBLIC LibRaw)
target_compile_definitions(raw_r PRIVATE LIBRAW_NOTHREADS NO_JASPER NO_JPEG NO_LCMS)
target_compile_options(raw_r PRIVATE -O2 -fexceptions -frtti -w)

add_library(filmdeskraw SHARED raw_jni.cpp)
target_compile_definitions(filmdeskraw PRIVATE LIBRAW_NOTHREADS)
target_compile_options(filmdeskraw PRIVATE -O2 -fexceptions -frtti)
target_link_libraries(filmdeskraw raw_r jnigraphics log)
