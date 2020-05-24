package com.me.picdemo;

import android.annotation.SuppressLint;
import android.graphics.SurfaceTexture;
import android.view.Surface;

public class InputSurface extends Surface {


    InputSurface mInputSurface=null;
    public InputSurface(SurfaceTexture surfaceTexture) {
        super(surfaceTexture);
    }

    @SuppressLint("NewApi")
    public InputSurface(Surface inputSurface) {
        super(new SurfaceTexture(false));
    }

    public InputSurface InputSurface(Surface inputSurface) {

        return this;
    }


    public void setPresentationTime(long l) {
    }

    public void swapBuffers() {
    }
}
