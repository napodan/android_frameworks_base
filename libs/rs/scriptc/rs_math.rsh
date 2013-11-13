#include "rs_cl.rsh"
#include "rs_core.rsh"



// Allocations
extern rs_allocation rsGetAllocation(const void *);
extern uint32_t rsAllocationGetDimX(rs_allocation);
extern uint32_t rsAllocationGetDimY(rs_allocation);
extern uint32_t rsAllocationGetDimZ(rs_allocation);
extern uint32_t rsAllocationGetDimLOD(rs_allocation);
extern uint32_t rsAllocationGetDimFaces(rs_allocation);



// Debugging
extern void __attribute__((overloadable))rsDebug(const char *, float);
extern void __attribute__((overloadable))rsDebug(const char *, float2);
extern void __attribute__((overloadable))rsDebug(const char *, float3);
extern void __attribute__((overloadable))rsDebug(const char *, float4);
extern void __attribute__((overloadable))rsDebug(const char *, int);
extern void __attribute__((overloadable))rsDebug(const char *, const void *);
#define RS_DEBUG(a) rsDebug(#a, a)
#define RS_DEBUG_MARKER rsDebug(__FILE__, __LINE__)

// RS Math
extern int __attribute__((overloadable)) rsRand(int);
extern int __attribute__((overloadable)) rsRand(int, int);
extern float __attribute__((overloadable)) rsRand(float);
extern float __attribute__((overloadable)) rsRand(float, float);

extern float __attribute__((overloadable)) rsFrac(float);

// time
extern int32_t rsSecond();
extern int32_t rsMinute();
extern int32_t rsHour();
extern int32_t rsDay();
extern int32_t rsMonth();
extern int32_t rsYear();
extern int64_t rsUptimeMillis();
extern int64_t rsStartTimeMillis();
extern int64_t rsElapsedTimeMillis();

extern int rsSendToClient(void *data, int cmdID, int len, int waitForSpace);

extern void rsMatrixLoadIdentity(rs_matrix4x4 *mat);
extern void rsMatrixLoadFloat(rs_matrix4x4 *mat, const float *f);
extern void rsMatrixLoadMat(rs_matrix4x4 *mat, const rs_matrix4x4 *newmat);
extern void rsMatrixLoadRotate(rs_matrix4x4 *mat, float rot, float x, float y, float z);
extern void rsMatrixLoadScale(rs_matrix4x4*mat, float x, float y, float z);
extern void rsMatrixLoadTranslate(rs_matrix4x4 *mat, float x, float y, float z);
extern void rsMatrixLoadMultiply(rs_matrix4x4 *mat, const rs_matrix4x4 *lhs, const rs_matrix4x4 *rhs);
extern void rsMatrixMultiply(rs_matrix4x4 *mat, const rs_matrix4x4 *rhs);
extern void rsMatrixRotate(rs_matrix4x4 *mat, float rot, float x, float y, float z);
extern void rsMatrixScale(rs_matrix4x4 *mat, float x, float y, float z);
extern void rsMatrixTranslate(rs_matrix4x4 *mat, float x, float y, float z);

// Script to Script
extern void __attribute__((overloadable))rsForEach(rs_script, rs_allocation input);
extern void __attribute__((overloadable))rsForEach(rs_script, rs_allocation input, rs_allocation output);
extern void __attribute__((overloadable))rsForEach(rs_script, rs_allocation input, int xStart, int xEnd);
extern void __attribute__((overloadable))rsForEach(rs_script, rs_allocation input, rs_allocation output, int xStart, int xEnd);
extern void __attribute__((overloadable))rsForEach(rs_script, rs_allocation input, int xStart, int yStart, int xEnd, int yEnd);
extern void __attribute__((overloadable))rsForEach(rs_script, rs_allocation input, rs_allocation output, int xStart, int yStart, int xEnd, int yEnd);


///////////////////////////////////////////////////////////////////
// non update funcs

/*
extern float3 float3Norm(float3);
extern float float3Length(float3);
extern float3 float3Add(float3 lhs, float3 rhs);
extern float3 float3Sub(float3 lhs, float3 rhs);
extern float3 float3Cross(float3 lhs, float3 rhs);
extern float float3Dot(float3 lhs, float3 rhs);
extern float3 float3Scale(float3 v, float scale);

extern float4 float4Add(float4 lhs, float4 rhs);
extern float4 float4Sub(float4 lhs, float4 rhs);
extern float4 float4Cross(float4 lhs, float4 rhs);
extern float float4Dot(float4 lhs, float4 rhs);
extern float4 float4Scale(float4 v, float scale);
*/


