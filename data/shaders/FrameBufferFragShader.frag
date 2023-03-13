#version 110 core
out vec4 FragColor;

uniform sampler2D screenTexture;
uniform float blurLevel;
uniform float texelOffset;
vec2 coord = gl_TexCoord[0].xy;

float pixelDistance = 1.0 / screen.x;
float scaledPixelDistance = scale * pixelDistance;
vec2 screenEdge = vec2(pixelDistance * 0.5, (pixelDistance * -0.5) + screen.y);

void main()
{
	vec4 color = vec4(0.0);
	int coreSize=3;
	int halfSize = (coreSize-1)/2;
	kernel[6] = 1; kernel[7] = 2; kernel[8] = 1;
	kernel[3] = 2; kernel[4] = 4; kernel[5] = 2;
	kernel[0] = 1; kernel[1] = 2; kernel[2] = 1;
	int index = 0;
	for(int y=0;y<coreSize;y++)
	{
		for(int x = 0;x<coreSize;x++)
		{
			vec4 currentColor = texture2D(screenTexture,coord+vec2((-halfSize+x)*texelOffset,(-halfSize+y)*texelOffset));
			color += currentColor*kernel[index];
			index++;
		}
	}
	color/=16.0;
	gl_FragColor=color;
}