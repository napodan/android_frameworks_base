/*
 * Copyright (C) 2009 The Android Open Source Project
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

#include "rsContext.h"

#include <GLES/gl.h>

using namespace android;
using namespace android::renderscript;


Element::Element(Context *rsc) : ObjectBase(rsc)
{
    mBits = 0;
    mAllocFile = __FILE__;
    mAllocLine = __LINE__;
    mFields = NULL;
    mFieldCount = 0;
}


Element::~Element()
{
    for (uint32_t ct = 0; ct < mRSC->mStateElement.mElements.size(); ct++) {
        if (mRSC->mStateElement.mElements[ct] == this) {
            mRSC->mStateElement.mElements.removeAt(ct);
            break;
        }
    }
    clear();
}

void Element::clear()
{
    delete [] mFields;
    mFields = NULL;
    mFieldCount = 0;
}

size_t Element::getSizeBits() const
{
    if (!mFieldCount) {
        return mBits;
    }

    size_t total = 0;
    for (size_t ct=0; ct < mFieldCount; ct++) {
        total += mFields[ct].e->mBits;
    }
    return total;
}

size_t Element::getFieldOffsetBits(uint32_t componentNumber) const
{
    size_t offset = 0;
    for (uint32_t ct = 0; ct < componentNumber; ct++) {
        offset += mFields[ct].e->mBits;
    }
    return offset;
}

void Element::dumpLOGV(const char *prefix) const
{
    ObjectBase::dumpLOGV(prefix);
    ALOGV("%s   Element: components %i,  size %i", prefix, mFieldCount, mBits);
    for (uint32_t ct = 0; ct < mFieldCount; ct++) {
        char buf[1024];
        sprintf(buf, "%s component %i: ", prefix, ct);
        //mComponents[ct]->dumpLOGV(buf);
    }
}


const Element * Element::create(Context *rsc, RsDataType dt, RsDataKind dk,
                            bool isNorm, uint32_t vecSize)
{
    // Look for an existing match.
    for (uint32_t ct=0; ct < rsc->mStateElement.mElements.size(); ct++) {
        const Element *ee = rsc->mStateElement.mElements[ct];
        if (!ee->getFieldCount() &&
            (ee->getComponent().getType() == dt) &&
            (ee->getComponent().getKind() == dk) &&
            (ee->getComponent().getIsNormalized() == isNorm) &&
            (ee->getComponent().getVectorSize() == vecSize)) {
            // Match
            ee->incUserRef();
            return ee;
        }
    }

    Element *e = new Element(rsc);
    e->mComponent.set(dt, dk, isNorm, vecSize);
    e->mBits = e->mComponent.getBits();
    rsc->mStateElement.mElements.push(e);
    return e;
}

const Element * Element::create(Context *rsc, size_t count, const Element **ein,
                            const char **nin, const size_t * lengths)
{
    // Look for an existing match.
    for (uint32_t ct=0; ct < rsc->mStateElement.mElements.size(); ct++) {
        const Element *ee = rsc->mStateElement.mElements[ct];
        if (ee->getFieldCount() == count) {
            bool match = true;
            for (uint32_t i=0; i < count; i++) {
                if ((ee->mFields[i].e.get() != ein[i]) ||
                    (ee->mFields[i].name.length() != lengths[i]) ||
                    (ee->mFields[i].name != nin[i])) {
                    match = false;
                    break;
                }
            }
            if (match) {
                ee->incUserRef();
                return ee;
            }
        }
    }

    Element *e = new Element(rsc);
    e->mFields = new ElementField_t [count];
    e->mFieldCount = count;
    for (size_t ct=0; ct < count; ct++) {
        e->mFields[ct].e.set(ein[ct]);
        e->mFields[ct].name.setTo(nin[ct], lengths[ct]);
    }

    rsc->mStateElement.mElements.push(e);
    return e;
}

String8 Element::getCStructBody(uint32_t indent) const
{
    String8 si;
    for (uint32_t ct=0; ct < indent; ct++) {
        si.append(" ");
    }

    String8 s(si);
    s.append("{\n");
    for (uint32_t ct = 0; ct < mFieldCount; ct++) {
        s.append(si);
        s.append(mFields[ct].e->getCType(indent+4));
        s.append(" ");
        s.append(mFields[ct].name);
        s.append(";\n");
    }
    s.append(si);
    s.append("}");
    return s;
}

String8 Element::getCType(uint32_t indent) const
{
    String8 s;
    for (uint32_t ct=0; ct < indent; ct++) {
        s.append(" ");
    }

    if (!mFieldCount) {
        // Basic component.
        s.append(mComponent.getCType());
    } else {
        s.append("struct ");
        s.append(getCStructBody(indent));
    }

    return s;
}

String8 Element::getGLSLType(uint32_t indent) const
{
    String8 s;
    for (uint32_t ct=0; ct < indent; ct++) {
        s.append(" ");
    }

    if (!mFieldCount) {
        // Basic component.
        s.append(mComponent.getGLSLType());
    } else {
        rsAssert(0);
        //s.append("struct ");
        //s.append(getCStructBody(indent));
    }

    return s;
}



ElementState::ElementState()
{
}

ElementState::~ElementState()
{
    rsAssert(!mElements.size());
}


/////////////////////////////////////////
//

namespace android {
namespace renderscript {

RsElement rsi_ElementCreate(Context *rsc,
                            RsDataType dt,
                            RsDataKind dk,
                            bool norm,
                            uint32_t vecSize)
{
    //ALOGE("rsi_ElementCreate %i %i %i %i", dt, dk, norm, vecSize);
    const Element *e = Element::create(rsc, dt, dk, norm, vecSize);
    e->incUserRef();
    return (RsElement)e;
}

RsElement rsi_ElementCreate2(Context *rsc,
                             size_t count,
                             const RsElement * ein,
                             const char ** names,
                             const size_t * nameLengths)
{
    //ALOGE("rsi_ElementCreate2 %i", count);
    const Element *e = Element::create(rsc, count, (const Element **)ein, names, nameLengths);
    e->incUserRef();
    return (RsElement)e;
}


}
}
