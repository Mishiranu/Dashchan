#pragma version(1)
#pragma rs java_package_name(com.mishiranu.dashchan.graphics)

float gammaCorrection;

void apply(uchar4 * inout) {
	float4 color = rsUnpackColor8888(*inout);
	color.rgb = clamp(pow(color.rgb, (float3) gammaCorrection), 0.f, 1.f);
	*inout = rsPackColorTo8888(color);
}
