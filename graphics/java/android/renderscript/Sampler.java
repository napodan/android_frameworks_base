/*
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.renderscript;


import java.io.IOException;
import java.io.InputStream;

import android.content.res.Resources;
import android.os.Bundle;
import android.util.Config;
import android.util.Log;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

/**
 * @hide
 *
 **/
public class Sampler extends BaseObj {
    public enum Value {
        NEAREST (0),
        LINEAR (1),
        LINEAR_MIP_LINEAR (2),
        WRAP (3),
        CLAMP (4);

        int mID;
        Value(int id) {
            mID = id;
        }
    }

    Sampler(int id, RenderScript rs) {
        super(rs);
        mID = id;
    }

    Sampler mSampler_CLAMP_NEAREST;
    Sampler mSampler_CLAMP_LINEAR;
    Sampler mSampler_CLAMP_LINEAR_MIP;
    Sampler mSampler_WRAP_NEAREST;
    Sampler mSampler_WRAP_LINEAR;
    Sampler mSampler_WRAP_LINEAR_MIP;

    public static Sampler CLAMP_NEAREST(RenderScript rs) {
        if(rs.mSampler_CLAMP_NEAREST == null) {
            Builder b = new Builder(rs);
            b.setMin(Value.NEAREST);
            b.setMag(Value.NEAREST);
            b.setWrapS(Value.CLAMP);
            b.setWrapT(Value.CLAMP);
            rs.mSampler_CLAMP_NEAREST = b.create();
        }
        return rs.mSampler_CLAMP_NEAREST;
    }

    public static Sampler CLAMP_LINEAR(RenderScript rs) {
        if(rs.mSampler_CLAMP_LINEAR == null) {
            Builder b = new Builder(rs);
            b.setMin(Value.LINEAR);
            b.setMag(Value.LINEAR);
            b.setWrapS(Value.CLAMP);
            b.setWrapT(Value.CLAMP);
            rs.mSampler_CLAMP_LINEAR = b.create();
        }
        return rs.mSampler_CLAMP_LINEAR;
    }

    public static Sampler CLAMP_LINEAR_MIP_LINEAR(RenderScript rs) {
        if(rs.mSampler_CLAMP_LINEAR_MIP_LINEAR == null) {
            Builder b = new Builder(rs);
            b.setMin(Value.LINEAR_MIP_LINEAR);
            b.setMag(Value.LINEAR_MIP_LINEAR);
            b.setWrapS(Value.CLAMP);
            b.setWrapT(Value.CLAMP);
            rs.mSampler_CLAMP_LINEAR_MIP_LINEAR = b.create();
        }
        return rs.mSampler_CLAMP_LINEAR_MIP_LINEAR;
    }

    public static Sampler WRAP_NEAREST(RenderScript rs) {
        if(rs.mSampler_WRAP_NEAREST == null) {
            Builder b = new Builder(rs);
            b.setMin(Value.NEAREST);
            b.setMag(Value.NEAREST);
            b.setWrapS(Value.WRAP);
            b.setWrapT(Value.WRAP);
            rs.mSampler_WRAP_NEAREST = b.create();
        }
        return rs.mSampler_WRAP_NEAREST;
    }

    public static Sampler WRAP_LINEAR(RenderScript rs) {
        if(rs.mSampler_WRAP_LINEAR == null) {
            Builder b = new Builder(rs);
            b.setMin(Value.LINEAR);
            b.setMag(Value.LINEAR);
            b.setWrapS(Value.WRAP);
            b.setWrapT(Value.WRAP);
            rs.mSampler_WRAP_LINEAR = b.create();
        }
        return rs.mSampler_WRAP_LINEAR;
    }

    public static Sampler WRAP_LINEAR_MIP_LINEAR(RenderScript rs) {
        if(rs.mSampler_WRAP_LINEAR_MIP_LINEAR == null) {
            Builder b = new Builder(rs);
            b.setMin(Value.LINEAR_MIP_LINEAR);
            b.setMag(Value.LINEAR_MIP_LINEAR);
            b.setWrapS(Value.WRAP);
            b.setWrapT(Value.WRAP);
            rs.mSampler_WRAP_LINEAR_MIP_LINEAR = b.create();
        }
        return rs.mSampler_WRAP_LINEAR_MIP_LINEAR;
    }


    public static class Builder {
        RenderScript mRS;
        Value mMin;
        Value mMag;
        Value mWrapS;
        Value mWrapT;
        Value mWrapR;

        public Builder(RenderScript rs) {
            mRS = rs;
            mMin = Value.NEAREST;
            mMag = Value.NEAREST;
            mWrapS = Value.WRAP;
            mWrapT = Value.WRAP;
            mWrapR = Value.WRAP;
        }

        public void setMin(Value v) {
            if (v == Value.NEAREST ||
                v == Value.LINEAR ||
                v == Value.LINEAR_MIP_LINEAR) {
                mMin = v;
            } else {
                throw new IllegalArgumentException("Invalid value");
            }
        }

        public void setMag(Value v) {
            if (v == Value.NEAREST || v == Value.LINEAR) {
                mMag = v;
            } else {
                throw new IllegalArgumentException("Invalid value");
            }
        }

        public void setWrapS(Value v) {
            if (v == Value.WRAP || v == Value.CLAMP) {
                mWrapS = v;
            } else {
                throw new IllegalArgumentException("Invalid value");
            }
        }

        public void setWrapT(Value v) {
            if (v == Value.WRAP || v == Value.CLAMP) {
                mWrapT = v;
            } else {
                throw new IllegalArgumentException("Invalid value");
            }
        }

        public void setWrapR(Value v) {
            if (v == Value.WRAP || v == Value.CLAMP) {
                mWrapR = v;
            } else {
                throw new IllegalArgumentException("Invalid value");
            }
        }

        static synchronized Sampler internalCreate(RenderScript rs, Builder b) {
            rs.nSamplerBegin();
            rs.nSamplerSet(0, b.mMin.mID);
            rs.nSamplerSet(1, b.mMag.mID);
            rs.nSamplerSet(2, b.mWrapS.mID);
            rs.nSamplerSet(3, b.mWrapT.mID);
            rs.nSamplerSet(4, b.mWrapR.mID);
            int id = rs.nSamplerCreate();
            return new Sampler(id, rs);
        }

        public Sampler create() {
            mRS.validate();
            return internalCreate(mRS, this);
        }
    }

}

