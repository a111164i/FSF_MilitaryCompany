#version 130 
uniform vec2 screen;
uniform float scale;
uniform float levelX;
uniform float levelY;
uniform float levelAlpha;
uniform sampler2D tex;
vec2 coord = gl_TexCoord[0].xy;

void main()
{
 
    float pixelDistanceX = levelX/(scale*screen.x);
    float pixelDistanceY = levelY/(scale*screen.x);
    vec4 color = vec4(0.0);
    int coreSize=5;
    int halfSize=coreSize/2;
    float kernel[25];
    kernel[20] = 1.0; kernel[21] = 1.0; kernel[22] = 1.0; kernel[23] = 1.0; kernel[24] = 1.0; 
    kernel[15] = 1.0; kernel[16] = 2.5; kernel[17] = 3.0; kernel[18] = 2.5; kernel[19] = 1.0; 
    kernel[10] = 1.0; kernel[11] = 3.0; kernel[12] = 5.0; kernel[13] = 3.0; kernel[14] = 1.0; 
    kernel[5]  = 1.0;  kernel[6] = 2.5;  kernel[7] = 3.0;  kernel[8] = 2.5;  kernel[9] = 1.0; 
    kernel[0]  = 1.0;  kernel[1] = 1.0;  kernel[2] = 1.0;  kernel[3] = 1.0;  kernel[4] = 1.0; 
    int index = 0;
    for(int y=0;y<coreSize;y++)
    {
			for(int x = 0;x<coreSize;x++)
			{
				//by texelOffset convert -1 form a fixed distance to certain percent of screen width
				vec4 currentColor = texture2D(tex,coord +vec2((-halfSize+float(x))*pixelDistanceX,(-halfSize+float(y))*pixelDistanceY)  );
				color += currentColor*kernel[index];
				index++;
			}
    }
    color/=45.0;
    gl_FragColor=color*vec4(1,1,1,levelAlpha);
	
    
	//gl_FragColor= vec4(coord.y,coord.y,coord.y,1.0);	
}
