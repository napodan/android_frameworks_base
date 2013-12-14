/*
 * Copyright (C) 2010 The Android Open Source Project
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

#ifndef ANDROID_UI_OPENGL_RENDERER_H
#define ANDROID_UI_OPENGL_RENDERER_H

#include <GLES2/gl2.h>
#include <GLES2/gl2ext.h>

#include <SkBitmap.h>
#include <SkMatrix.h>
#include <SkPaint.h>
#include <SkXfermode.h>

#include <utils/RefBase.h>

#include "Matrix.h"
#include "Program.h"
#include "Rect.h"
#include "Snapshot.h"
#include "Texture.h"
#include "TextureCache.h"

namespace android {
namespace uirenderer {

///////////////////////////////////////////////////////////////////////////////
// Support
///////////////////////////////////////////////////////////////////////////////

/**
 * Simple structure to describe a vertex with a position.
 * This is used to draw filled rectangles without a texture.
 */
struct SimpleVertex {
    float position[2];
}; // struct SimpleVertex

/**
 * Simple structure to describe a vertex with a position and a texture.
 */
struct TextureVertex {
    float position[2];
    float texture[2];
}; // struct TextureVertex

/**
 * Structure mapping Skia xfermodes to OpenGL blending factors.
 */
struct Blender {
    SkXfermode::Mode mode;
    GLenum src;
    GLenum dst;
}; // struct Blender

///////////////////////////////////////////////////////////////////////////////
// Renderer
///////////////////////////////////////////////////////////////////////////////

/**
 * OpenGL renderer used to draw accelerated 2D graphics. The API is a
 * simplified version of Skia's Canvas API.
 */
class OpenGLRenderer {
public:
    OpenGLRenderer();
    ~OpenGLRenderer();

    void setViewport(int width, int height);
    void prepare();

    int getSaveCount() const;
    int save(int flags);
    void restore();
    void restoreToCount(int saveCount);

    int saveLayer(float left, float top, float right, float bottom, const SkPaint* p, int flags);
    int saveLayerAlpha(float left, float top, float right, float bottom, int alpha, int flags);

    void translate(float dx, float dy);
    void rotate(float degrees);
    void scale(float sx, float sy);

    void setMatrix(SkMatrix* matrix);
    void getMatrix(SkMatrix* matrix);
    void concatMatrix(SkMatrix* matrix);

    const Rect& getClipBounds();
    bool quickReject(float left, float top, float right, float bottom);
    bool clipRect(float left, float top, float right, float bottom);

    void drawBitmap(SkBitmap* bitmap, float left, float top, const SkPaint* paint);
    void drawBitmap(SkBitmap* bitmap, const SkMatrix* matrix, const SkPaint* paint);
    void drawBitmap(SkBitmap* bitmap, float srcLeft, float srcTop, float srcRight, float srcBottom,
            float dstLeft, float dstTop, float dstRight, float dstBottom, const SkPaint* paint);
    void drawColor(int color, SkXfermode::Mode mode);
    void drawRect(float left, float top, float right, float bottom, const SkPaint* paint);

private:
    /**
     * Saves the current state of the renderer as a new snapshot.
     * The new snapshot is saved in mSnapshot and the previous snapshot
     * is linked from mSnapshot->previous.
     *
     * @return The new save count. This value can be passed to #restoreToCount()
     */
    int saveSnapshot();

    /**
     * Restores the current snapshot; mSnapshot becomes mSnapshot->previous.
     *
     * @return True if the clip should be also reapplied by calling
     *         #setScissorFromClip().
     */
    bool restoreSnapshot();

    /**
     * Sets the clipping rectangle using glScissor. The clip is defined by
     * the current snapshot's clipRect member.
     */
    void setScissorFromClip();

    /**
     * Compose the layer defined in the current snapshot with the layer
     * defined by the previous snapshot.
     *
     * The current snapshot *must* be a layer (flag kFlagIsLayer set.)
     *
     * @param curent The current snapshot containing the layer to compose
     * @param previous The previous snapshot to compose the current layer with
     */
    void composeLayer(sp<Snapshot> current, sp<Snapshot> previous);

    /**
     * Creates a new layer stored in the specified snapshot.
     *
     * @param snapshot The snapshot associated with the new layer
     * @param left The left coordinate of the layer
     * @param top The top coordinate of the layer
     * @param right The right coordinate of the layer
     * @param bottom The bottom coordinate of the layer
     * @param alpha The translucency of the layer
     * @param mode The blending mode of the layer
     * @param flags The layer save flags
     *
     * @return True if the layer was successfully created, false otherwise
     */
    bool createLayer(sp<Snapshot> snapshot, float left, float top, float right, float bottom,
            int alpha, SkXfermode::Mode mode, int flags);

    /**
     * Draws a colored rectangle with the specified color. The specified coordinates
     * are transformed by the current snapshot's transform matrix.
     *
     * @param left The left coordinate of the rectangle
     * @param top The top coordinate of the rectangle
     * @param right The right coordinate of the rectangle
     * @param bottom The bottom coordinate of the rectangle
     * @param color The rectangle's ARGB color, defined as a packed 32 bits word
     * @param mode The Skia xfermode to use
     */
    void drawColorRect(float left, float top, float right, float bottom,
    		int color, SkXfermode::Mode mode);

    /**
     * Draws a textured rectangle with the specified texture. The specified coordinates
     * are transformed by the current snapshot's transform matrix.
     *
     * @param left The left coordinate of the rectangle
     * @param top The top coordinate of the rectangle
     * @param right The right coordinate of the rectangle
     * @param bottom The bottom coordinate of the rectangle
     * @param texture The texture name to map onto the rectangle
     * @param alpha An additional translucency parameter, between 0.0f and 1.0f
     * @param mode The blending mode
     * @param blend True if the texture contains an alpha channel
     * @param isPremultiplied Indicates whether the texture has premultiplied alpha
     */
    void drawTextureRect(float left, float top, float right, float bottom, GLuint texture,
            float alpha, SkXfermode::Mode mode, bool blend, bool isPremultiplied = false);

    /**
     * Resets the texture coordinates stored in mDrawTextureVertices. Setting the values
     * back to default is achieved by calling:
     *
     * resetDrawTextureTexCoords(0.0f, 0.0f, 1.0f, 1.0f);
     *
     * @param u1 The left coordinate of the texture
     * @param v1 The bottom coordinate of the texture
     * @param u2 The right coordinate of the texture
     * @param v2 The top coordinate of the texture
     */
    void resetDrawTextureTexCoords(float u1, float v1, float u2, float v2);

    /**
     * Gets the alpha and xfermode out of a paint object. If the paint is null
     * alpha will be 255 and the xfermode will be SRC_OVER.
     *
     * @param paint The paint to extract values from
     * @param alpha Where to store the resulting alpha
     * @param mode Where to store the resulting xfermode
     */
    inline void getAlphaAndMode(const SkPaint* paint, int* alpha, SkXfermode::Mode* mode);

    // Dimensions of the drawing surface
    int mWidth, mHeight;

    // Matrix used for ortho projection in shaders
    float mOrthoMatrix[16];

    // Model-view matrix used to position/size objects
    mat4 mModelView;

    // Number of saved states
    int mSaveCount;
    // Base state
    Snapshot mFirstSnapshot;
    // Current state
    sp<Snapshot> mSnapshot;

    // Shaders
    sp<DrawColorProgram> mDrawColorShader;
    sp<DrawTextureProgram> mDrawTextureShader;

    // Used to draw textured quads
    TextureVertex mDrawTextureVertices[4];

    // Used to cache all drawBitmap textures
    TextureCache mTextureCache;
}; // class OpenGLRenderer

}; // namespace uirenderer
}; // namespace android

#endif // ANDROID_UI_OPENGL_RENDERER_H
