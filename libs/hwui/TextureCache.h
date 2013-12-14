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

#ifndef ANDROID_UI_TEXTURE_CACHE_H
#define ANDROID_UI_TEXTURE_CACHE_H

#include <SkBitmap.h>

#include "Texture.h"
#include "GenerationCache.h"

namespace android {
namespace uirenderer {

/**
 * A simple LRU texture cache. The cache has a maximum size expressed in bytes.
 * Any texture added to the cache causing the cache to grow beyond the maximum
 * allowed size will also cause the oldest texture to be kicked out.
 */
class TextureCache: public OnEntryRemoved<SkBitmap*, Texture*> {
public:
    TextureCache(unsigned int maxByteSize);
    ~TextureCache();

    /**
     * Used as a callback when an entry is removed from the cache.
     * Do not invoke directly.
     */
    void operator()(SkBitmap* bitmap, Texture* texture);

    /**
     * Returns the texture associated with the specified bitmap. If the texture
     * cannot be found in the cache, a new texture is generated.
     */
    Texture* get(SkBitmap* bitmap);
    /**
     * Removes the texture associated with the specified bitmap. Returns NULL
     * if the texture cannot be found. Upon remove the texture is freed.
     */
    void remove(SkBitmap* bitmap);
    /**
     * Clears the cache. This causes all textures to be deleted.
     */
    void clear();

    /**
     * Sets the maximum size of the cache in bytes.
     */
    void setMaxSize(unsigned int maxSize);
    /**
     * Returns the maximum size of the cache in bytes.
     */
    unsigned int getMaxSize();
    /**
     * Returns the current size of the cache in bytes.
     */
    unsigned int getSize();

private:
    /**
     * Generates the texture from a bitmap into the specified texture structure.
     *
     * @param regenerate If true, the bitmap data is reuploaded into the texture, but
     *        no new texture is generated.
     */
    void generateTexture(SkBitmap* bitmap, Texture* texture, bool regenerate = false);

    GenerationCache<SkBitmap, Texture> mCache;

    unsigned int mSize;
    unsigned int mMaxSize;
}; // class TextureCache

}; // namespace uirenderer
}; // namespace android

#endif // ANDROID_UI_TEXTURE_CACHE_H
