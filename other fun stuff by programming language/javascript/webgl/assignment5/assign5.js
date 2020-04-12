var canvas;
var program;
var gl;
var colourCanvas, light1ColourCanvas, light2ColourCanvas;
var colourGl;

var numTimesToSubdivide = 5;
 
var index = 0;
var pointsArray = [];
var normalsArray = [];

// for ortho
var onear = -6.0;
var ofar = 0.3;

// for perspective
var pnear = 0.1;
var pfar = 6.0;    // back (too small and the obj is beyond and don't see it)
var radius = 3.0; // distance from eye (too small and obj fills the canvas)
var fovy = 45.0;  // Field-of-view in Y direction angle (in degrees)
var aspect = 1.0;       // Viewport aspect ratio

var theta  = 1.0;
var phi    = 1.0;
var dr = 5.0 * Math.PI/180.0;

var left = -1.0;
var right = 1.0;
var ytop = 1.0;
var bottom = -1.0;

var eye;
const at = vec3(0.0, 0.0, 0.0); 
const up = vec3(0.0, 1.0, 0.0);

var mouseTheta  = 0.0;
var mousePhi    = 0.0;

var locationSliderScale = 1.0/20.0;
var radiusSliderScale = 0.1;
var projectionMatrix, projectionMatrixLoc;

var mouseDown = false;
var lastMouseX = null;
var lastMouseY = null;

var modelViewMatrix = mat4();
var modelViewMatrixLoc;

var worldShapes = [];

var colours = [
    vec4( 0.0, 0.0, 0.0, 1.0 ),  // black
    vec4( 1.0, 1.0, 1.0, 1.0 ),  // white
    vec4( 1.0, 0.0, 0.0, 1.0 ),  // red
    vec4( 1.0, 1.0, 0.0, 1.0 ),  // yellow
    vec4( 0.0, 1.0, 0.0, 1.0 ),  // green
    vec4( 0.0, 0.0, 1.0, 1.0 ),  // blue
    vec4( 1.0, 0.0, 1.0, 1.0 ),  // magenta
    vec4( 0.0, 1.0, 1.0, 1.0)   // cyan
];    

var cindex = 1, light1Cidx = 1, light2Cidx = 2;

// for lighting
var nBuffer, vBuffer;

var lightTheta1 = 0.0;
var lightPosition1 = vec4(1.0, 1.0, 2.0, 0.0 );
var lightTheta2 = 0.0;
var lightPosition2 = vec4(0.0, 0.0, 0.0, 0.0 );
var lightOn1 = 1.0; // use value instead of boolean, as didn't seem to work
var lightOn2 = 1.0;
var lightDist1 = 1.0;
var lightDist2 = 1.0;
var lightAmbient = vec4(0.2, 0.2, 0.2, 1.0 );
var light1Diffuse = colours[1];
var light1Specular = colours[1];
var light2Diffuse = colours[1];
var light2Specular = colours[1];
var materialAmbient = vec4( 1.0, 0.0, 1.0, 1.0 );
var materialDiffuse = vec4( 1.0, 0.8, 0.0, 1.0 );
var materialSpecular = vec4( 1.0, 1.0, 1.0, 1.0 );

var ambientProductLoc, diffuseProductLoc, specularProductLoc, lightPositionLoc, shininessLoc, lightOnLoc, lightDistLoc;
var materialShininess = 20.0;

var ambientColor, diffuseColor, specularColor;

var normMatrix, normMatrixLoc;

var texSize = 128;

// Create a checkerboard pattern using floats
var image1 = new Array();
    for (var i =0; i<texSize; i++)  image1[i] = new Array();
    for (var i =0; i<texSize; i++)
        for ( var j = 0; j < texSize; j++)
           image1[i][j] = new Float32Array(4);
    for (var i =0; i<texSize; i++) for (var j=0; j<texSize; j++) {
        var c = (((i & 0x8) == 0) ^ ((j & 0x8)  == 0));
        image1[i][j] = [c, c, c, 1];
    }

// Convert floats to ubytes for texture

var image2 = new Uint8Array(4*texSize*texSize);

    for ( var i = 0; i < texSize; i++ )
        for ( var j = 0; j < texSize; j++ )
           for(var k =0; k<4; k++)
                image2[4*texSize*i+4*j+k] = 255*image1[i][j][k];

var imageLoaded = false;
var image = new Image();
var textureImg;
var texture; // checkerboard
var lastShapeImageType = "none";

function configureTexture( image ) {
    textureImg = gl.createTexture();
//    bindJpgImage();
}

function bindJpgImage() {
    gl.bindTexture( gl.TEXTURE_2D, textureImg );
    gl.pixelStorei(gl.UNPACK_FLIP_Y_WEBGL, true);
    gl.texImage2D( gl.TEXTURE_2D, 0, gl.RGB, gl.RGB, gl.UNSIGNED_BYTE, image );
    gl.generateMipmap( gl.TEXTURE_2D );
    gl.texParameteri( gl.TEXTURE_2D, gl.TEXTURE_MIN_FILTER, gl.NEAREST_MIPMAP_LINEAR );
    gl.texParameteri( gl.TEXTURE_2D, gl.TEXTURE_MAG_FILTER, gl.NEAREST );

    gl.uniform1i(gl.getUniformLocation(program, "texture"), 0);
}
    
function triangle(a, b, c) {

     var t1 = subtract(b, a);
     var t2 = subtract(c, a);
     var normal = normalize(cross(t2, t1));
     normal = vec4(normal);

    // changed from the default, works really well for the sphere
     normalsArray.push(a);
     normalsArray.push(b);
     normalsArray.push(c);
     
     pointsArray.push(a);
     pointsArray.push(b);      
     pointsArray.push(c);

     index += 3;
}

function divideTriangle(a, b, c, count) {
    if ( count > 0 ) {
                
        var ab = normalize(mix( a, b, 0.5), true);
        var ac = normalize(mix( a, c, 0.5), true);
        var bc = normalize(mix( b, c, 0.5), true);
                                
        divideTriangle( a, ab, ac, count - 1 );
        divideTriangle( ab, b, bc, count - 1 );
        divideTriangle( bc, c, ac, count - 1 );
        divideTriangle( ab, bc, ac, count - 1 );
    }
    else { // draw tetrahedron at end of recursion
        triangle( a, b, c );
    }
}

function tetrahedron(a, b, c, d, n) {
    divideTriangle(a, b, c, n);
    divideTriangle(d, c, b, n);
    divideTriangle(a, d, b, n);
    divideTriangle(a, c, d, n);
}

window.onload = function init() {
    // canvas is for displaying all the objects together
    canvas = document.getElementById( "gl-world-canvas" );

    gl = WebGLUtils.setupWebGL( canvas );
    if ( !gl ) { alert( "WebGL isn't available" ); }

    gl.viewport( 0, 0, canvas.width, canvas.height );
    gl.clearColor( .1, .1, .1, 1.0 );

    initColourPicker(document.getElementById( "colour-canvas" ), 0);
    initColourPicker(document.getElementById( "light1-colour-canvas" ), 1);
    initColourPicker(document.getElementById( "light2-colour-canvas" ), 2);

    //
    //  Load shaders and initialize attribute buffers
    //
    program = initShaders( gl, "vertex-world-shader", "fragment-world-shader" );
    gl.useProgram( program );
    
    initShapeOption("sphere");
    initWorldPoints(program);
    
    document.getElementById("newbtn").onclick = function() {

        // the current shape is only in the points array so far, now it has to be added to the shapes arrays
        
        var dimenParam = [ parseFloat(document.getElementById("radSlider").value) * radiusSliderScale,
            parseFloat(document.getElementById("hSlider").value) * radiusSliderScale ];

        dimenParam[1] = dimenParam[0]; // sphere doesn't use height
            
        var worldShape = {
            pointsArray: pointsArray,
            normalsArray: normalsArray,
            index: index,
            location: [ parseFloat(document.getElementById("xLocSlider").value) * locationSliderScale,
                        parseFloat(document.getElementById("yLocSlider").value) * locationSliderScale,
                        parseFloat(document.getElementById("zLocSlider").value) * locationSliderScale ],
            rotation: [ parseFloat(document.getElementById("xRotSlider").value),
                        parseFloat(document.getElementById("yRotSlider").value),
                        parseFloat(document.getElementById("zRotSlider").value) ],
            dimensions: dimenParam,
            shape: "sphere",
            colour: cindex,
            imgtype: document.getElementById("imgtype").value
        }

        worldShapes.push(worldShape);
        
        // default the inputs, and the shape to sphere again
        defaultValues();
        index = 0;
        cindex = 1;
        pointsArray = []; 
        normalsArray = [];
        initShapeOption("sphere");
    }

    document.getElementById("resetview").onclick = function() {
        mouseTheta = 0.0;
        mousePhi = 0.0;
    }

    document.getElementById("light1on").onchange = function() {
        lightOn1 = document.getElementById("light1on").checked ? 1.0 : 0.0;
    }

    document.getElementById("light2on").onchange = function() {
        lightOn2 = document.getElementById("light2on").checked ? 1.0 : 0.0;
    }

    render();
}

function defaultValues() {
    document.getElementById("xLocSlider").value = 0;
    document.getElementById("yLocSlider").value = 0;
    document.getElementById("zLocSlider").value = 0;
    document.getElementById("xRotSlider").value = 0;
    document.getElementById("yRotSlider").value = 0;
    document.getElementById("zRotSlider").value = 0;
    document.getElementById("radSlider").value = 3; // radius
    document.getElementById("imgtype").value = 'checkers';
}

function initColourPicker(canvas, which) {
    var context = canvas.getContext('2d');

    var bw = canvas.width / colours.length;
    var bh = canvas.height;
    for (var i = 0; i < colours.length; i++) {
        var bx = bw * i;
        // add 4 points for this colour
        for (var j = 0; j < 4; j++) {
            var px = (j == 0 ? bx : bx + bw);
            var rgbStr = "rgb("+parseInt(colours[i][0]*255)+","+parseInt(colours[i][1]*255)+","+parseInt(colours[i][2]*255)+")";
            context.fillStyle = rgbStr;
            context.fillRect (px,0,bw,bh);
        }
    } 
    
    canvas.addEventListener("click", function(event) {
        var rect = canvas.getBoundingClientRect();
        cIdx = parseInt((event.clientX-rect.left) / (canvas.width / colours.length));
        if (which == 0) {
            cindex = cIdx;
        }
        else if (which == 1) {
            light1Cidx = cIdx;
            light1Diffuse = colours[cIdx];
            light1Specular = colours[cIdx];
        }
        else {
            light2Cidx = cIdx;
            light2Diffuse = colours[cIdx];
            light2Specular = colours[cIdx];
        }
    });
        
}

// set up points array
function initShapeOption(shape) {
    pointsArray = [];
    normalsArray = [];
    index = 0;
    if (shape == "sphere") {
        var va = vec4(0.0, 0.0, -1.0, 1);
        var vb = vec4(0.0, 0.942809, 0.333333, 1);
        var vc = vec4(-0.816497, -0.471405, 0.333333, 1);
        var vd = vec4(0.816497, -0.471405, 0.333333, 1);
        
        tetrahedron(va, vb, vc, vd, numTimesToSubdivide);
    }
    else {
        alert("init shape todo "+shape);
    }
}

// the world is initialized with the first shape
function initWorldPoints(program) {

    gl.enable(gl.DEPTH_TEST);
    gl.depthFunc(gl.LEQUAL);
    gl.enable(gl.POLYGON_OFFSET_FILL);
    gl.polygonOffset(1.0, 2.0);

    modelViewMatrixLoc = gl.getUniformLocation( program, "modelViewMatrix" );    
    projectionMatrixLoc = gl.getUniformLocation( program, "projectionMatrix" );
    normMatrixLoc = gl.getUniformLocation( program, "normalMatrix" );

    ambientProductLoc = gl.getUniformLocation(program, "ambientProduct");
    diffuseProductLoc = gl.getUniformLocation(program, "diffuseProduct");
    specularProductLoc = gl.getUniformLocation(program, "specularProduct");	
    lightPositionLoc = gl.getUniformLocation(program, "lightPosition");
    shininessLoc = gl.getUniformLocation(program, "shininess");
    lightOnLoc = gl.getUniformLocation(program, "lightOn");
    lightDistLoc = gl.getUniformLocation(program, "lightDist");

    vBuffer = gl.createBuffer();
    gl.bindBuffer( gl.ARRAY_BUFFER, vBuffer);
    
    var vPosition = gl.getAttribLocation( program, "vPosition");
    gl.vertexAttribPointer( vPosition, 4, gl.FLOAT, false, 0, 0);
    gl.enableVertexAttribArray( vPosition);        
    
    nBuffer = gl.createBuffer();
    gl.bindBuffer( gl.ARRAY_BUFFER, nBuffer);
    
    var vNormal = gl.getAttribLocation( program, "vNormal" );
    gl.vertexAttribPointer( vNormal, 4, gl.FLOAT, false, 0, 0 );
    gl.enableVertexAttribArray( vNormal);

    texture = gl.createTexture();
    bindCheckers();

    image.crossOrigin = "anonymous";
    image.src = "https://s3.amazonaws.com/glowscript/textures/rug_texture.jpg";
    image.onload = function() { 
        configureTexture( image );
        imageLoaded = true;
    }

    canvas.onmousedown = handleMouseDown;
    document.onmouseup = handleMouseUp;
    document.onmousemove = handleMouseMove;
}

function bindCheckers() {
    gl.bindTexture( gl.TEXTURE_2D, texture );
    gl.pixelStorei(gl.UNPACK_FLIP_Y_WEBGL, true);
    gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGBA, texSize, texSize, 0,
             gl.RGBA, gl.UNSIGNED_BYTE, image2);
    gl.generateMipmap( gl.TEXTURE_2D );
    gl.texParameteri( gl.TEXTURE_2D, gl.TEXTURE_MIN_FILTER,
                      gl.NEAREST_MIPMAP_LINEAR );
    gl.texParameteri( gl.TEXTURE_2D, gl.TEXTURE_MAG_FILTER, gl.NEAREST );

    gl.uniform1i(gl.getUniformLocation(program, "texture"), 0);
}

function handleMouseDown(event) {
    mouseDown = true;
    lastMouseX = event.clientX;
    lastMouseY = event.clientY;
}

function handleMouseUp(event) {
    mouseDown = false;
}

function handleMouseMove(event) {
    if (!mouseDown) {
        return;
    }
    var newX = event.clientX;
    var newY = event.clientY;

    var deltaX = newX - lastMouseX
    mouseTheta -= deltaX / 100;

    var deltaY = newY - lastMouseY; // not using this at the moment
    mousePhi -= deltaY / 100;

    if (mouseTheta >= 1.0) {
        mouseTheta = 0.0;
    }
    else if (mouseTheta <= -1.0) {
        mouseTheta = 0.99;
    }
    if (mousePhi >= 1.0) {
        mousePhi = 0.0;
    }
    else if (mousePhi <= -1.0) {
        mousePhi = 0.99;
    }

    lastMouseX = newX
    lastMouseY = newY;
}

function render() {
    
    renderWorldCanvas();
    window.requestAnimFrame(render);
}

function updateLightPosition() {
    
    // move the first light in a circle on the z plane
    lightTheta1 += .02;

    var thetaRad = radians(lightTheta1);
    lightPosition1[0] = radius * Math.cos(lightTheta1);
    lightPosition1[1] = radius * Math.sin(lightTheta1);

    // 2nd one circles on the x plane
    lightTheta2 += .03;
    
    var thetaRad = radians(lightTheta2);
    lightPosition2[1] = radius * Math.cos(lightTheta2);
    lightPosition2[2] = radius * Math.sin(lightTheta2);
    
    gl.uniform4fv( lightPositionLoc, flatten([lightPosition1, lightPosition2]) );
    gl.uniform1fv( lightOnLoc, flatten([lightOn1, lightOn2]) );
    
    lightDist1 = parseFloat(document.getElementById("light1-dist-slider").value);
    lightDist2 = parseFloat(document.getElementById("light2-dist-slider").value);
    
    gl.uniform1fv( lightDistLoc, flatten([lightDist1, lightDist2]) );
}

function renderWorldCanvas() {
    
    gl.clear( gl.COLOR_BUFFER_BIT | gl.DEPTH_BUFFER_BIT);

    theta = mouseTheta*360.0;
    // the mouse drag inc/decrements the x rotation of the whole world (moves the eye)
    eye = vec3(radius*Math.sin(radians(theta))*Math.cos(radians(phi)), 
           radius*Math.sin(radians(theta))*Math.sin(radians(phi)), 
           radius*Math.cos(radians(theta)));
 
    modelViewMatrix = lookAt(eye, at, up); 

    updateLightPosition();
        
    if (true) {// perspective broke for some reason, no time to debug it now (document.getElementById("perspective").checked) {
        projectionMatrix = perspective(fovy, aspect, pnear, pfar);
    }
    else {
        projectionMatrix = ortho(left, right, bottom, ytop, onear, ofar);
    }

    gl.uniformMatrix4fv(projectionMatrixLoc, false, flatten(projectionMatrix) );

    // the current object is not in the world yet
    var locParam = [ parseFloat(document.getElementById("xLocSlider").value) * locationSliderScale,
        parseFloat(document.getElementById("yLocSlider").value) * locationSliderScale,
        parseFloat(document.getElementById("zLocSlider").value) * locationSliderScale ];
        
    var rotParam = [ parseFloat(document.getElementById("xRotSlider").value),
        parseFloat(document.getElementById("yRotSlider").value),
        parseFloat(document.getElementById("zRotSlider").value) ];

    var dimenParam = [ parseFloat(document.getElementById("radSlider").value) * radiusSliderScale,
        parseFloat(document.getElementById("hSlider").value) * radiusSliderScale ];
        
    dimenParam[1] = dimenParam[0]; // sphere doesn't use height
    var imgTypeParam = document.getElementById("imgtype").value;
    
    renderShapeToWorld(pointsArray, normalsArray, index, locParam, rotParam, dimenParam, cindex, imgTypeParam);    

    // draw all the other shapes already added to the world
    for (var i = 0; i < worldShapes.length; i++) {
        var ws = worldShapes[i];
        renderShapeToWorld(ws.pointsArray, ws.normalsArray,
            ws.index, ws.location, ws.rotation, ws.dimensions,
            ws.colour, ws.imgtype); 
    }
}

function renderShapeToWorld(pointsParam, normalsParam, indexParam, locParam, rotParam, dimenParam, colourParam, imgTypeParam) {
    
    gl.bindBuffer(gl.ARRAY_BUFFER, vBuffer);
    gl.bufferData( gl.ARRAY_BUFFER, flatten(pointsParam), gl.STATIC_DRAW);

    gl.bindBuffer( gl.ARRAY_BUFFER, nBuffer);
    gl.bufferData( gl.ARRAY_BUFFER, flatten(normalsParam), gl.STATIC_DRAW );

    var instanceMatrix = mult(modelViewMatrix, translate(locParam[0], locParam[1], locParam[2]));
    instanceMatrix = mult(instanceMatrix, rotate(rotParam[0], 1, 0, 0));
    instanceMatrix = mult(instanceMatrix, rotate(rotParam[1], 0, 1, 0));
    instanceMatrix = mult(instanceMatrix, rotate(rotParam[2], 0, 0, 1));
    instanceMatrix = mult(instanceMatrix, scalem(dimenParam[0], dimenParam[1], dimenParam[0]));

    gl.uniformMatrix4fv( modelViewMatrixLoc, false, flatten(instanceMatrix) );

    normMatrix = normalMatrix(instanceMatrix, true); 
    gl.uniformMatrix3fv(normMatrixLoc, false, flatten(normMatrix) );

    ambientProduct = mult(lightAmbient, mult( colours[colourParam], materialAmbient) );
    diffuseProduct = [ mult(light1Diffuse, mult( colours[colourParam], materialDiffuse) ), 
                        mult(light2Diffuse, mult( colours[colourParam], materialDiffuse) )];
    specularProduct = [ mult(light1Specular, materialSpecular), 
                        mult(light2Specular, materialSpecular) ];

    gl.uniform4fv( ambientProductLoc, flatten(ambientProduct) );
    gl.uniform4fv( diffuseProductLoc, flatten(diffuseProduct) );
    gl.uniform4fv( specularProductLoc, flatten(specularProduct) );
    gl.uniform1f( shininessLoc, materialShininess );
    
    // detect change in image type and bind the appropriate texture
    if (lastShapeImageType != imgTypeParam) {
        lastShapeImageType = imgTypeParam;
        if (imgTypeParam == 'checkers') {
            bindCheckers();
        }
        else {
            bindJpgImage();
        }
    }

    for( var i=0; i<indexParam; i+=3) {
        gl.drawArrays( gl.TRIANGLES, i, 3 );
    }
}
