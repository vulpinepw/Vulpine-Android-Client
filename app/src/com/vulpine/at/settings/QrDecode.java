package com.vulpine.at.settings;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.PlanarYUVLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;
import java.util.EnumMap;
import java.util.Map;

final class QrDecode {
    static final QRCodeReader R = new QRCodeReader();
    static final Map<DecodeHintType, Object> H = new EnumMap<>(DecodeHintType.class);

    static {
        H.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
        H.put(DecodeHintType.POSSIBLE_FORMATS, java.util.Collections.singletonList(BarcodeFormat.QR_CODE));
    }

    static String yuv(byte[] data, int w, int h) {
        int side = Math.min(w, h);
        int left = (w - side) / 2, top = (h - side) / 2;
        String r = tryOne(new PlanarYUVLuminanceSource(data, w, h, left, top, side, side, false));
        if (r == null) r = tryOne(new PlanarYUVLuminanceSource(data, w, h, 0, 0, w, h, false));
        return r;
    }

    static String tryOne(PlanarYUVLuminanceSource src) {
        try {
            Result res = R.decode(new BinaryBitmap(new HybridBinarizer(src)), H);
            return res == null ? null : res.getText();
        } catch (Exception e) {
            try {
                Result res = R.decode(new BinaryBitmap(new HybridBinarizer(src.invert())), H);
                return res == null ? null : res.getText();
            } catch (Exception e2) {
                return null;
            } finally {
                R.reset();
            }
        } finally {
            R.reset();
        }
    }
}
