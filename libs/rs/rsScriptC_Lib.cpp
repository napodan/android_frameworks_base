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
#include "rsScriptC.h"
#include "rsMatrix.h"

#include "acc/acc.h"
#include "utils/Timers.h"

#include <time.h>

using namespace android;
using namespace android::renderscript;

#define GET_TLS()  Context::ScriptTLSStruct * tls = \
    (Context::ScriptTLSStruct *)pthread_getspecific(Context::gThreadTLSKey); \
    Context * rsc = tls->mContext; \
    ScriptC * sc = (ScriptC *) tls->mScript




//////////////////////////////////////////////////////////////////////////////
// Non-Updated code below
//////////////////////////////////////////////////////////////////////////////

typedef struct {
    float x;
    float y;
    float z;
} vec3_t;

typedef struct {
    float x;
    float y;
    float z;
    float w;
} vec4_t;

typedef struct {
    float x;
    float y;
} vec2_t;


//////////////////////////////////////////////////////////////////////////////
// Vec3 routines
//////////////////////////////////////////////////////////////////////////////

static void SC_vec3Norm(vec3_t *v)
{
    float len = sqrtf(v->x * v->x + v->y * v->y + v->z * v->z);
    len = 1 / len;
    v->x *= len;
    v->y *= len;
    v->z *= len;
}

static float SC_vec3Length(const vec3_t *v)
{
    return sqrtf(v->x * v->x + v->y * v->y + v->z * v->z);
}

static void SC_vec3Add(vec3_t *dest, const vec3_t *lhs, const vec3_t *rhs)
{
    dest->x = lhs->x + rhs->x;
    dest->y = lhs->y + rhs->y;
    dest->z = lhs->z + rhs->z;
}

static void SC_vec3Sub(vec3_t *dest, const vec3_t *lhs, const vec3_t *rhs)
{
    dest->x = lhs->x - rhs->x;
    dest->y = lhs->y - rhs->y;
    dest->z = lhs->z - rhs->z;
}

static void SC_vec3Cross(vec3_t *dest, const vec3_t *lhs, const vec3_t *rhs)
{
    float x = lhs->y * rhs->z  - lhs->z * rhs->y;
    float y = lhs->z * rhs->x  - lhs->x * rhs->z;
    float z = lhs->x * rhs->y  - lhs->y * rhs->x;
    dest->x = x;
    dest->y = y;
    dest->z = z;
}

static float SC_vec3Dot(const vec3_t *lhs, const vec3_t *rhs)
{
    return lhs->x * rhs->x + lhs->y * rhs->y + lhs->z * rhs->z;
}

static void SC_vec3Scale(vec3_t *lhs, float scale)
{
    lhs->x *= scale;
    lhs->y *= scale;
    lhs->z *= scale;
}

//////////////////////////////////////////////////////////////////////////////
// Vec4 routines
//////////////////////////////////////////////////////////////////////////////

static void SC_vec4Norm(vec4_t *v)
{
    float len = sqrtf(v->x * v->x + v->y * v->y + v->z * v->z + v->w * v->w);
    len = 1 / len;
    v->x *= len;
    v->y *= len;
    v->z *= len;
    v->w *= len;
}

static float SC_vec4Length(const vec4_t *v)
{
    return sqrtf(v->x * v->x + v->y * v->y + v->z * v->z + v->w * v->w);
}

static void SC_vec4Add(vec4_t *dest, const vec4_t *lhs, const vec4_t *rhs)
{
    dest->x = lhs->x + rhs->x;
    dest->y = lhs->y + rhs->y;
    dest->z = lhs->z + rhs->z;
    dest->w = lhs->w + rhs->w;
}

static void SC_vec4Sub(vec4_t *dest, const vec4_t *lhs, const vec4_t *rhs)
{
    dest->x = lhs->x - rhs->x;
    dest->y = lhs->y - rhs->y;
    dest->z = lhs->z - rhs->z;
    dest->w = lhs->w - rhs->w;
}

static float SC_vec4Dot(const vec4_t *lhs, const vec4_t *rhs)
{
    return lhs->x * rhs->x + lhs->y * rhs->y + lhs->z * rhs->z + lhs->w * rhs->w;
}

static void SC_vec4Scale(vec4_t *lhs, float scale)
{
    lhs->x *= scale;
    lhs->y *= scale;
    lhs->z *= scale;
    lhs->w *= scale;
}

//////////////////////////////////////////////////////////////////////////////
// Math routines
//////////////////////////////////////////////////////////////////////////////

static float SC_sinf_fast(float x)
{
    const float A =   1.0f / (2.0f * M_PI);
    const float B = -16.0f;
    const float C =   8.0f;

    // scale angle for easy argument reduction
    x *= A;

    if (fabsf(x) >= 0.5f) {
        // argument reduction
        x = x - ceilf(x + 0.5f) + 1.0f;
    }

    const float y = B * x * fabsf(x) + C * x;
    return 0.2215f * (y * fabsf(y) - y) + y;
}

static float SC_cosf_fast(float x)
{
    x += float(M_PI / 2);

    const float A =   1.0f / (2.0f * M_PI);
    const float B = -16.0f;
    const float C =   8.0f;

    // scale angle for easy argument reduction
    x *= A;

    if (fabsf(x) >= 0.5f) {
        // argument reduction
        x = x - ceilf(x + 0.5f) + 1.0f;
    }

    const float y = B * x * fabsf(x) + C * x;
    return 0.2215f * (y * fabsf(y) - y) + y;
}


static float SC_randf(float max)
{
    float r = (float)rand();
    return r / RAND_MAX * max;
}

static float SC_randf2(float min, float max)
{
    float r = (float)rand();
    return r / RAND_MAX * (max - min) + min;
}

static int SC_randi(int max)
{
    return (int)SC_randf(max);
}

static int SC_randi2(int min, int max)
{
    return (int)SC_randf2(min, max);
}

static int SC_clamp(int amount, int low, int high)
{
    return amount < low ? low : (amount > high ? high : amount);
}

static float SC_roundf(float v)
{
    return floorf(v + 0.4999999999);
}

static float SC_distf2(float x1, float y1, float x2, float y2)
{
    float x = x2 - x1;
    float y = y2 - y1;
    return sqrtf(x * x + y * y);
}

static float SC_distf3(float x1, float y1, float z1, float x2, float y2, float z2)
{
    float x = x2 - x1;
    float y = y2 - y1;
    float z = z2 - z1;
    return sqrtf(x * x + y * y + z * z);
}

static float SC_magf2(float a, float b)
{
    return sqrtf(a * a + b * b);
}

static float SC_magf3(float a, float b, float c)
{
    return sqrtf(a * a + b * b + c * c);
}

static float SC_frac(float v)
{
    int i = (int)floor(v);
    return fmin(v - i, 0x1.fffffep-1f);
}

//////////////////////////////////////////////////////////////////////////////
// Time routines
//////////////////////////////////////////////////////////////////////////////

static int32_t SC_second()
{
    GET_TLS();

    time_t rawtime;
    time(&rawtime);

    struct tm *timeinfo;
    timeinfo = localtime(&rawtime);
    return timeinfo->tm_sec;
}

static int32_t SC_minute()
{
    GET_TLS();

    time_t rawtime;
    time(&rawtime);

    struct tm *timeinfo;
    timeinfo = localtime(&rawtime);
    return timeinfo->tm_min;
}

static int32_t SC_hour()
{
    GET_TLS();

    time_t rawtime;
    time(&rawtime);

    struct tm *timeinfo;
    timeinfo = localtime(&rawtime);
    return timeinfo->tm_hour;
}

static int32_t SC_day()
{
    GET_TLS();

    time_t rawtime;
    time(&rawtime);

    struct tm *timeinfo;
    timeinfo = localtime(&rawtime);
    return timeinfo->tm_mday;
}

static int32_t SC_month()
{
    GET_TLS();

    time_t rawtime;
    time(&rawtime);

    struct tm *timeinfo;
    timeinfo = localtime(&rawtime);
    return timeinfo->tm_mon;
}

static int32_t SC_year()
{
    GET_TLS();

    time_t rawtime;
    time(&rawtime);

    struct tm *timeinfo;
    timeinfo = localtime(&rawtime);
    return timeinfo->tm_year;
}

static int64_t SC_uptimeMillis2()
{
    return nanoseconds_to_milliseconds(systemTime(SYSTEM_TIME_MONOTONIC));
}

static int64_t SC_startTimeMillis2()
{
    GET_TLS();
    return sc->mEnviroment.mStartTimeMillis;
}

static int64_t SC_elapsedTimeMillis2()
{
    GET_TLS();
    return nanoseconds_to_milliseconds(systemTime(SYSTEM_TIME_MONOTONIC))
            - sc->mEnviroment.mStartTimeMillis;
}

static int32_t SC_uptimeMillis()
{
    return nanoseconds_to_milliseconds(systemTime(SYSTEM_TIME_MONOTONIC));
}

static int32_t SC_startTimeMillis()
{
    GET_TLS();
    return sc->mEnviroment.mStartTimeMillis;
}

static int32_t SC_elapsedTimeMillis()
{
    GET_TLS();
    return nanoseconds_to_milliseconds(systemTime(SYSTEM_TIME_MONOTONIC))
            - sc->mEnviroment.mStartTimeMillis;
}

//////////////////////////////////////////////////////////////////////////////
// Matrix routines
//////////////////////////////////////////////////////////////////////////////


static void SC_matrixLoadIdentity(rsc_Matrix *mat)
{
    Matrix *m = reinterpret_cast<Matrix *>(mat);
    m->loadIdentity();
}

static void SC_matrixLoadFloat(rsc_Matrix *mat, const float *f)
{
    Matrix *m = reinterpret_cast<Matrix *>(mat);
    m->load(f);
}

static void SC_matrixLoadMat(rsc_Matrix *mat, const rsc_Matrix *newmat)
{
    Matrix *m = reinterpret_cast<Matrix *>(mat);
    m->load(reinterpret_cast<const Matrix *>(newmat));
}

static void SC_matrixLoadRotate(rsc_Matrix *mat, float rot, float x, float y, float z)
{
    Matrix *m = reinterpret_cast<Matrix *>(mat);
    m->loadRotate(rot, x, y, z);
}

static void SC_matrixLoadScale(rsc_Matrix *mat, float x, float y, float z)
{
    Matrix *m = reinterpret_cast<Matrix *>(mat);
    m->loadScale(x, y, z);
}

static void SC_matrixLoadTranslate(rsc_Matrix *mat, float x, float y, float z)
{
    Matrix *m = reinterpret_cast<Matrix *>(mat);
    m->loadTranslate(x, y, z);
}

static void SC_matrixLoadMultiply(rsc_Matrix *mat, const rsc_Matrix *lhs, const rsc_Matrix *rhs)
{
    Matrix *m = reinterpret_cast<Matrix *>(mat);
    m->loadMultiply(reinterpret_cast<const Matrix *>(lhs),
                    reinterpret_cast<const Matrix *>(rhs));
}

static void SC_matrixMultiply(rsc_Matrix *mat, const rsc_Matrix *rhs)
{
    Matrix *m = reinterpret_cast<Matrix *>(mat);
    m->multiply(reinterpret_cast<const Matrix *>(rhs));
}

static void SC_matrixRotate(rsc_Matrix *mat, float rot, float x, float y, float z)
{
    Matrix *m = reinterpret_cast<Matrix *>(mat);
    m->rotate(rot, x, y, z);
}

static void SC_matrixScale(rsc_Matrix *mat, float x, float y, float z)
{
    Matrix *m = reinterpret_cast<Matrix *>(mat);
    m->scale(x, y, z);
}

static void SC_matrixTranslate(rsc_Matrix *mat, float x, float y, float z)
{
    Matrix *m = reinterpret_cast<Matrix *>(mat);
    m->translate(x, y, z);
}


//////////////////////////////////////////////////////////////////////////////
//
//////////////////////////////////////////////////////////////////////////////

static uint32_t SC_allocGetDimX(RsAllocation va)
{
    GET_TLS();
    const Allocation *a = static_cast<const Allocation *>(va);
    //LOGE("SC_allocGetDimX a=%p", a);
    //LOGE(" type=%p", a->getType());
    return a->getType()->getDimX();
}

static uint32_t SC_allocGetDimY(RsAllocation va)
{
    GET_TLS();
    const Allocation *a = static_cast<const Allocation *>(va);
    return a->getType()->getDimY();
}

static uint32_t SC_allocGetDimZ(RsAllocation va)
{
    GET_TLS();
    const Allocation *a = static_cast<const Allocation *>(va);
    return a->getType()->getDimZ();
}

static uint32_t SC_allocGetDimLOD(RsAllocation va)
{
    GET_TLS();
    const Allocation *a = static_cast<const Allocation *>(va);
    return a->getType()->getDimLOD();
}

static uint32_t SC_allocGetDimFaces(RsAllocation va)
{
    GET_TLS();
    const Allocation *a = static_cast<const Allocation *>(va);
    return a->getType()->getDimFaces();
}



static void SC_debugF(const char *s, float f) {
    ALOGE("%s %f, 0x%08x", s, f, *((int *) (&f)));
}
static void SC_debugFv2(const char *s, rsvF_2 fv) {
    float *f = (float *)&fv;
    ALOGE("%s {%f, %f}", s, f[0], f[1]);
}
static void SC_debugFv3(const char *s, rsvF_4 fv) {
    float *f = (float *)&fv;
    ALOGE("%s {%f, %f, %f}", s, f[0], f[1], f[2]);
}
static void SC_debugFv4(const char *s, rsvF_4 fv) {
    float *f = (float *)&fv;
    ALOGE("%s {%f, %f, %f, %f}", s, f[0], f[1], f[2], f[3]);
}
static void SC_debugI32(const char *s, int32_t i) {
    ALOGE("%s %i  0x%x", s, i, i);
}

static uchar4 SC_convertColorTo8888_f3(float r, float g, float b) {
    uchar4 t;
    t.f[0] = (uint8_t)(r * 255.f);
    t.f[1] = (uint8_t)(g * 255.f);
    t.f[2] = (uint8_t)(b * 255.f);
    t.f[3] = 0xff;
    return t;
}

static uchar4 SC_convertColorTo8888_f4(float r, float g, float b, float a) {
    uchar4 t;
    t.f[0] = (uint8_t)(r * 255.f);
    t.f[1] = (uint8_t)(g * 255.f);
    t.f[2] = (uint8_t)(b * 255.f);
    t.f[3] = (uint8_t)(a * 255.f);
    return t;
}

static uint32_t SC_toClient(void *data, int cmdID, int len, int waitForSpace)
{
    GET_TLS();
    //LOGE("SC_toClient %i %i %i", cmdID, len, waitForSpace);
    return rsc->sendMessageToClient(data, cmdID, len, waitForSpace != 0);
}

static void SC_scriptCall(int scriptID)
{
    GET_TLS();
    rsc->runScript((Script *)scriptID);
}

int SC_divsi3(int a, int b)
{
    return a / b;
}

int SC_getAllocation(const void *ptr)
{
    GET_TLS();
    const Allocation *alloc = sc->ptrToAllocation(ptr);
    return (int)alloc;
}


void SC_ForEachii(RsScript vs, RsAllocation vin)
{
    GET_TLS();
    Script *s = static_cast<Script *>(vs);
    Allocation *ain = static_cast<Allocation *>(vin);
    s->runForEach(rsc, ain, NULL);
}

void SC_ForEachiii(RsScript vs, RsAllocation vin, RsAllocation vout)
{
    GET_TLS();
    Script *s = static_cast<Script *>(vs);
    Allocation *ain = static_cast<Allocation *>(vin);
    Allocation *aout = static_cast<Allocation *>(vout);
    s->runForEach(rsc, ain, aout);
}

void SC_ForEachiiii(RsScript vs, RsAllocation vin, int xStart, int xEnd)
{
    GET_TLS();
    Script *s = static_cast<Script *>(vs);
    Allocation *ain = static_cast<Allocation *>(vin);
    s->runForEach(rsc, ain, NULL, xStart, xEnd);
}

void SC_ForEachiiiii(RsScript vs, RsAllocation vin, RsAllocation vout, int xStart, int xEnd)
{
    GET_TLS();
    Script *s = static_cast<Script *>(vs);
    Allocation *ain = static_cast<Allocation *>(vin);
    Allocation *aout = static_cast<Allocation *>(vout);
    s->runForEach(rsc, ain, aout, xStart, xEnd);
}

void SC_ForEachiiiiii(RsScript vs, RsAllocation vin, int xStart, int yStart, int xEnd, int yEnd)
{
    GET_TLS();
    Script *s = static_cast<Script *>(vs);
    Allocation *ain = static_cast<Allocation *>(vin);
    s->runForEach(rsc, ain, NULL, xStart, yStart, xEnd, yEnd);
}

void SC_ForEachiiiiiii(RsScript vs, RsAllocation vin, RsAllocation vout, int xStart, int yStart, int xEnd, int yEnd)
{
    GET_TLS();
    Script *s = static_cast<Script *>(vs);
    Allocation *ain = static_cast<Allocation *>(vin);
    Allocation *aout = static_cast<Allocation *>(vout);
    s->runForEach(rsc, ain, aout, xStart, yStart, xEnd, yEnd);
}


//////////////////////////////////////////////////////////////////////////////
// Class implementation
//////////////////////////////////////////////////////////////////////////////

// llvm name mangling ref
//  <builtin-type> ::= v  # void
//                 ::= b  # bool
//                 ::= c  # char
//                 ::= a  # signed char
//                 ::= h  # unsigned char
//                 ::= s  # short
//                 ::= t  # unsigned short
//                 ::= i  # int
//                 ::= j  # unsigned int
//                 ::= l  # long
//                 ::= m  # unsigned long
//                 ::= x  # long long, __int64
//                 ::= y  # unsigned long long, __int64
//                 ::= f  # float
//                 ::= d  # double

static ScriptCState::SymbolTable_t gSyms[] = {
    { "__divsi3", (void *)&SC_divsi3 },

    // allocation
    { "rsAllocationGetDimX", (void *)&SC_allocGetDimX },
    { "rsAllocationGetDimY", (void *)&SC_allocGetDimY },
    { "rsAllocationGetDimZ", (void *)&SC_allocGetDimZ },
    { "rsAllocationGetDimLOD", (void *)&SC_allocGetDimLOD },
    { "rsAllocationGetDimFaces", (void *)&SC_allocGetDimFaces },
    { "rsGetAllocation", (void *)&SC_getAllocation },

    // color
    { "_Z17rsPackColorTo8888fff", (void *)&SC_convertColorTo8888_f3 },
    { "_Z17rsPackColorTo8888ffff", (void *)&SC_convertColorTo8888_f4 },
    //extern uchar4 __attribute__((overloadable)) rsPackColorTo8888(float3);
    //extern uchar4 __attribute__((overloadable)) rsPackColorTo8888(float4);
    //extern float4 rsUnpackColor8888(uchar4);
    //extern uchar4 __attribute__((overloadable)) rsPackColorTo565(float r, float g, float b);
    //extern uchar4 __attribute__((overloadable)) rsPackColorTo565(float3);
    //extern float4 rsUnpackColor565(uchar4);

    // Debug
    { "_Z7rsDebugPKcf", (void *)&SC_debugF },
    { "_Z7rsDebugPKcDv2_f", (void *)&SC_debugFv2 },
    { "_Z7rsDebugPKcDv3_f", (void *)&SC_debugFv3 },
    { "_Z7rsDebugPKcDv4_f", (void *)&SC_debugFv4 },
    { "_Z7rsDebugPKci", (void *)&SC_debugI32 },
    //extern void __attribute__((overloadable))rsDebug(const char *, const void *);


    // RS Math
    { "_Z6rsRandi", (void *)&SC_randi },
    { "_Z6rsRandii", (void *)&SC_randi2 },
    { "_Z6rsRandf", (void *)&SC_randf },
    { "_Z6rsRandff", (void *)&SC_randf2 },
    { "_Z6rsFracf", (void *)&SC_frac },

    // time
    { "rsSecond", (void *)&SC_second },
    { "rsMinute", (void *)&SC_minute },
    { "rsHour", (void *)&SC_hour },
    { "rsDay", (void *)&SC_day },
    { "rsMonth", (void *)&SC_month },
    { "rsYear", (void *)&SC_year },
    { "rsUptimeMillis", (void*)&SC_uptimeMillis2 },
    { "rsStartTimeMillis", (void*)&SC_startTimeMillis2 },
    { "rsElapsedTimeMillis", (void*)&SC_elapsedTimeMillis2 },

    { "rsSendToClient", (void *)&SC_toClient },

    // matrix
    { "rsMatrixLoadIdentity", (void *)&SC_matrixLoadIdentity },
    { "rsMatrixLoadFloat", (void *)&SC_matrixLoadFloat },
    { "rsMatrixLoadMat", (void *)&SC_matrixLoadMat },
    { "rsMatrixLoadRotate", (void *)&SC_matrixLoadRotate },
    { "rsMatrixLoadScale", (void *)&SC_matrixLoadScale },
    { "rsMatrixLoadTranslate", (void *)&SC_matrixLoadTranslate },
    { "rsMatrixLoadMultiply", (void *)&SC_matrixLoadMultiply },
    { "rsMatrixMultiply", (void *)&SC_matrixMultiply },
    { "rsMatrixRotate", (void *)&SC_matrixRotate },
    { "rsMatrixScale", (void *)&SC_matrixScale },
    { "rsMatrixTranslate", (void *)&SC_matrixTranslate },

    { "_Z9rsForEachii", (void *)&SC_ForEachii },
    { "_Z9rsForEachiii", (void *)&SC_ForEachiii },
    { "_Z9rsForEachiiii", (void *)&SC_ForEachiiii },
    { "_Z9rsForEachiiiii", (void *)&SC_ForEachiiiii },
    { "_Z9rsForEachiiiiii", (void *)&SC_ForEachiiiiii },
    { "_Z9rsForEachiiiiiii", (void *)&SC_ForEachiiiiiii },

////////////////////////////////////////////////////////////////////

    //{ "sinf_fast", (void *)&SC_sinf_fast },
    //{ "cosf_fast", (void *)&SC_cosf_fast },
    //{ "clamp", (void *)&SC_clamp },
    //{ "distf2", (void *)&SC_distf2 },
    //{ "distf3", (void *)&SC_distf3 },
    //{ "magf2", (void *)&SC_magf2 },
    //{ "magf3", (void *)&SC_magf3 },
    //{ "mapf", (void *)&SC_mapf },

    { "scriptCall", (void *)&SC_scriptCall },


    { NULL, NULL }
};

const ScriptCState::SymbolTable_t * ScriptCState::lookupSymbol(const char *sym)
{
    ScriptCState::SymbolTable_t *syms = gSyms;

    while (syms->mPtr) {
        if (!strcmp(syms->mName, sym)) {
            return syms;
        }
        syms++;
    }
    return NULL;
}

