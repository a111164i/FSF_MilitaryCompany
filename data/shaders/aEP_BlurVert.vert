#version 130 
void main()
{
	gl_Position = ftransform();
	//gl_Position is a Built-in Variable which describe the relative location to viewport of this current pixel
	//clamp to (-1,1)
	//lower left is (-1,-1), upper right is (1,1)
	gl_TexCoord[0] = gl_MultiTexCoord0;
}