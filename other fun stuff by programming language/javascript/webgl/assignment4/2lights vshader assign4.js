
//TODO
// improve colours
// improve lighting, especially default pos.. why only z-coord makes any diff?
var canvas;
var gl;
var colourCanvas;
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

var cindex = 4;
var black = flatten(colours[0]);
var white = flatten(colours[1]);
var green = flatten(colours[4]);
var blue = flatten(colours[5]);
var red = flatten(colours[2]);

// for lighting
var nBuffer, vBuffer;

var lightTheta1 = 0.0;
var lightPosition1 = vec4(1.0, 1.0, 2.0, 0.0 );
var lightTheta2 = 0.0;
var lightPosition2 = vec4(0.0, 0.0, 0.0, 0.0 );
/*
var lightAmbient = vec4(0.3, 0.3, 0.3, 1.0 );
var lightDiffuse = vec4( 0.6, 0.6, 0.6, 1.0 );
var lightSpecular = vec4( 1.0, 1.0, 1.0, 1.0 );

var materialAmbient = vec4( 0.2, 0.2, 0.2, 1.0 );
var materialSpecular = vec4( 1.0, 1.0, 1.0, 1.0 );
var materialDiffuse = vec4( 0.2, 0.2, 0.2, 1.0 );
*/
var lightAmbient = vec4(0.2, 0.2, 0.2, 1.0 );
var lightDiffuse = vec4( 1.0, 1.0, 1.0, 1.0 );
var lightSpecular = vec4( 1.0, 1.0, 1.0, 1.0 );
var materialAmbient = vec4( 1.0, 0.0, 1.0, 1.0 );
var materialDiffuse = vec4( 1.0, 0.8, 0.0, 1.0 );
var materialSpecular = vec4( 1.0, 1.0, 1.0, 1.0 );

var ambientProductLoc, diffuseProductLoc, specularProductLoc, lightPositionLoc, shininessLoc;
var materialShininess = 20.0;

var ambientColor, diffuseColor, specularColor;

var normalMatrix, normalMatrixLoc;

function triangle(a, b, c) {

     var t1 = subtract(b, a);
     var t2 = subtract(c, a);
     var normal = normalize(cross(t2, t1));
     normal = vec4(normal);

     normalsArray.push(normal);
     normalsArray.push(normal);
     normalsArray.push(normal);
     
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

//    aspect =  parseFloat( canvas.width/canvas.height );

    initColourPicker();

    //
    //  Load shaders and initialize attribute buffers
    //
    var program = initShaders( gl, "vertex-world-shader", "fragment-world-shader" );
    gl.useProgram( program );
    
    initShapeOption("sphere");
    initWorldPoints(program);
    
    document.getElementById("newbtn").onclick = function() {
        
        // the current shape is only in the points array so far, now it has to be added to the shapes arrays
        
        var dimenParam = [ parseFloat(document.getElementById("radSlider").value) * radiusSliderScale,
            parseFloat(document.getElementById("hSlider").value) * radiusSliderScale ];

        if (document.getElementById("shape").value == "sphere") {
            dimenParam[1] = dimenParam[0]; // sphere doesn't use height
        }
            
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
            shape: document.getElementById("shape").value,
            colour: cindex
        }

        worldShapes.push(worldShape);
        
        // default the inputs, and the shape to sphere again
        defaultValues();
        index = 0;
        cindex = 4;
        pointsArray = []; 
        normalsArray = [];
        initShapeOption("sphere");
    }

    document.getElementById("shape").onchange = function() {
        if (document.getElementById("shape").value == "sphere") {
            document.getElementById("hSlider").disabled = true;
            document.getElementById("hPrompt").style.color = 'grey';
        }
        else {
            document.getElementById("hSlider").disabled = false;
            document.getElementById("hPrompt").style.color = 'black';
        }        
        
        initShapeOption(document.getElementById("shape").value);
    }

    document.getElementById("resetview").onclick = function() {
        mouseTheta = 0.0;
        mousePhi = 0.0;
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
    document.getElementById("hSlider").value = 3; // height
    document.getElementById("hSlider").disabled = true;
    document.getElementById("hPrompt").style.color = 'grey';
    document.getElementById("shape").value = "sphere";
    
}

function initColourPicker() {
    var canvas = document.getElementById( "colour-canvas" );
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
        cindex = parseInt((event.clientX-rect.left) / (canvas.width / colours.length));
    });
        
}

// set up points array
function initShapeOption(shape) {
    pointsArray = [];
    index = 0;
    if (shape == "sphere") {
        var va = vec4(0.0, 0.0, -1.0, 1);
        var vb = vec4(0.0, 0.942809, 0.333333, 1);
        var vc = vec4(-0.816497, -0.471405, 0.333333, 1);
        var vd = vec4(0.816497, -0.471405, 0.333333, 1);
        
        tetrahedron(va, vb, vc, vd, numTimesToSubdivide);
    }
    else if (shape == "cylinder") {
        makeCylinder(1, 1);
    }
    else if (shape == "cone") {
        makeCylinder(1, 0);
    }
    else {
        alert("init shape todo "+shape);
    }
}

function makeCylinder(r1, r2) { // for a cone, r2 = 0
    var TWOPI = Math.PI * 2.0;
    var NFACETS = 360;
    var P1 = vec4(0.0, 1, 0.0, 1);
    var P2 = vec4(0.0, -1, 0.0, 1);
    var A = vec3(0.0, 0.0, -1);
    var B = vec3(-1, 0.0, 0.0);
    var x = 0, y = 1, z = 2;

    for (i=0; i < NFACETS;i++) {

        var q = [];
        var n = 0;
        q[n] = vec4(0, 0, 0, 1);
        var theta1 = i * TWOPI / NFACETS;
        var theta2 = (i+1) * TWOPI / NFACETS;
        q[n][x] = P1[x] + r1 * Math.cos(theta1) * A[x] + r1 * Math.sin(theta1) * B[x]
        q[n][y] = P1[y] + r1 * Math.cos(theta1) * A[y] + r1 * Math.sin(theta1) * B[y]
        q[n][z] = P1[z] + r1 * Math.cos(theta1) * A[z] + r1 * Math.sin(theta1) * B[z]
        n++;
        q[n] = vec4(0, 0, 0, 1);
        q[n][x] = P2[x] + r2 * Math.cos(theta1) * A[x] + r2 * Math.sin(theta1) * B[x]
        q[n][y] = P2[y] + r2 * Math.cos(theta1) * A[y] + r2 * Math.sin(theta1) * B[y]
        q[n][z] = P2[z] + r2 * Math.cos(theta1) * A[z] + r2 * Math.sin(theta1) * B[z]
        n++;
        if (r1 != 0) {
            q[n] = vec4(0, 0, 0, 1);
            q[n][x] = P1[x] + r1 * Math.cos(theta2) * A[x] + r1 * Math.sin(theta2) * B[x]
            q[n][y] = P1[y] + r1 * Math.cos(theta2) * A[y] + r1 * Math.sin(theta2) * B[y]
            q[n][z] = P1[z] + r1 * Math.cos(theta2) * A[z] + r1 * Math.sin(theta2) * B[z]
            n++;
        }
        if (r2 != 0) {
            q[n] = vec4(0, 0, 0, 1);
            q[n][x] = P2[x] + r2 * Math.cos(theta2) * A[x] + r2 * Math.sin(theta2) * B[x]
            q[n][y] = P2[y] + r2 * Math.cos(theta2) * A[y] + r2 * Math.sin(theta2) * B[y]
            q[n][z] = P2[z] + r2 * Math.cos(theta2) * A[z] + r2 * Math.sin(theta2) * B[z]
            n++;
        }
        
        triangle(P1, q[2], q[0]);
        triangle(q[0], q[2], q[1]);
        if (r2 != 0) {
            triangle(q[1], q[3], q[2]);
            triangle(P2, q[1], q[3]);
        }
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
    normalMatrixLoc = gl.getUniformLocation( program, "normalMatrix" );

    ambientProductLoc = gl.getUniformLocation(program, "ambientProduct");
    diffuseProductLoc = gl.getUniformLocation(program, "diffuseProduct");
    specularProductLoc = gl.getUniformLocation(program, "specularProduct");	
    lightPositionLoc = gl.getUniformLocation(program, "lightPosition");
    shininessLoc = gl.getUniformLocation(program, "shininess");

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


    canvas.onmousedown = handleMouseDown;
    document.onmouseup = handleMouseUp;
    document.onmousemove = handleMouseMove;
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

    //console.log('theta='+mouseTheta+' phi='+mousePhi+' deltax='+deltaX);
    
    lastMouseX = newX
    lastMouseY = newY;
    
}

function render() {
    
    renderWorldCanvas();
    window.requestAnimFrame(render);
}

/*
 * Borrowed this from http://jsfiddle.net/mkleene/bokrf5nm/light/
 */
function getLookAt() {

    var cameraPhi = (mousePhi * 2 * Math.PI);
    var cameraTheta = (mouseTheta * 2 * Math.PI) + (Math.PI / 2);
    if (cameraTheta > (2 * Math.PI)) {
        cameraTheta -= (2 * Math.PI);
    }

    var xDir = Math.sin(cameraTheta) * Math.sin(cameraPhi);
    var yDir = Math.cos(cameraTheta);
    var zDir = Math.sin(cameraTheta) * Math.cos(cameraPhi);

    var cameraX = xDir * 45.0;
    var cameraY = yDir * 45.0;
    var cameraZ = zDir * 45.0 - 45.0;
    var up;

    if (cameraTheta === 0.0) {
        console.error('bad bad bad');
    } else if (cameraTheta === Math.PI) {
        console.log('also bad bad bad');
    } else {
        up = cross([xDir, yDir, zDir], [0.0, 1.0, 0.0]);
        up = cross(up, [xDir, yDir, zDir]);
        if ((cameraTheta >= 0.0) && (cameraTheta <= Math.PI)) {
//            up = negate(up);
        }
        else {
            up = negate(up);
        }
            
/*        
        up = vec3.create();
        vec3.cross(up, [xDir, yDir, zDir], [0.0, 1.0, 0.0]);
        vec3.cross(up, up, [xDir, yDir, zDir]);
        if ((cameraTheta >= 0.0) && (cameraTheta <= Math.PI)) {
            vec3.negate(up, up);
        }
*/
    }

/*
    var lookAt = mat4.create();
    mat4.lookAt(lookAt, [cameraX, cameraY, cameraZ], [0.0, 0.0, -45.0], up);
    return lookAt;
*/
    return lookAt([cameraX, cameraY, cameraZ], [0.0, 0.0, -45.0], up);
//    return lookAt([cameraX, cameraY, cameraZ], at, up);
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
}

function renderWorldCanvas() {
    
    updateLightPosition();
    
    gl.clear( gl.COLOR_BUFFER_BIT | gl.DEPTH_BUFFER_BIT);

    theta = mouseTheta*360.0;
    // the mouse drag inc/decrements the x rotation of the whole world (moves the eye)
    eye = vec3(radius*Math.sin(radians(theta))*Math.cos(radians(phi)), 
           radius*Math.sin(radians(theta))*Math.sin(radians(phi)), 
           radius*Math.cos(radians(theta)));
 
    modelViewMatrix = lookAt(eye, at, up); 
    
    if (document.getElementById("perspective").checked) {
        projectionMatrix = perspective(fovy, aspect, pnear, pfar);
    }
    else {
        projectionMatrix = ortho(left, right, bottom, ytop, onear, ofar);
    }
try {    
//console.log('mv='+modelViewMatrix+' lookat='+getLookAt());
}
catch(e) {}
//    projectionMatrix = mult(projectionMatrix, getLookAt());

    gl.uniformMatrix4fv(projectionMatrixLoc, false, flatten(projectionMatrix) );
//    gl.uniform4fv( lightPositionLoc, flatten( mult(vec4(eye), lightPosition1) ) );
//    gl.uniform4fv( lightPositionLoc, flatten( mult(modelViewMatrix, lightPosition1) ) );
//    gl.uniform4fv( lightPositionLoc, flatten( lightPosition1 ) );
    

    // the current object is not in the world yet
    var locParam = [ parseFloat(document.getElementById("xLocSlider").value) * locationSliderScale,
        parseFloat(document.getElementById("yLocSlider").value) * locationSliderScale,
        parseFloat(document.getElementById("zLocSlider").value) * locationSliderScale ];
        
    var rotParam = [ parseFloat(document.getElementById("xRotSlider").value),
        parseFloat(document.getElementById("yRotSlider").value),
        parseFloat(document.getElementById("zRotSlider").value) ];

    var dimenParam = [ parseFloat(document.getElementById("radSlider").value) * radiusSliderScale,
        parseFloat(document.getElementById("hSlider").value) * radiusSliderScale ];
        
    if (document.getElementById("shape").value == "sphere") {
        dimenParam[1] = dimenParam[0]; // sphere doesn't use height
    }
    
    renderShapeToWorld(pointsArray, normalsArray, index, locParam, rotParam, dimenParam, cindex);    

    // draw all the other shapes already added to the world
    for (var i = 0; i < worldShapes.length; i++) {
        var ws = worldShapes[i];
        renderShapeToWorld(ws.pointsArray, ws.normalsArray,
            ws.index, ws.location, ws.rotation, ws.dimensions,
            ws.colour); 
    }
}

function renderShapeToWorld(pointsParam, normalsParam, indexParam, locParam, rotParam, dimenParam, colourParam) {
    
    gl.bindBuffer(gl.ARRAY_BUFFER, vBuffer);
    gl.bufferData( gl.ARRAY_BUFFER, flatten(pointsParam), gl.STATIC_DRAW);

    gl.bindBuffer( gl.ARRAY_BUFFER, nBuffer);
    gl.bufferData( gl.ARRAY_BUFFER, flatten(normalsArray), gl.STATIC_DRAW );

    var instanceMatrix = mult(modelViewMatrix, translate(locParam[0], locParam[1], locParam[2]));
    instanceMatrix = mult(instanceMatrix, rotate(rotParam[0], 1, 0, 0));
    instanceMatrix = mult(instanceMatrix, rotate(rotParam[1], 0, 1, 0));
    instanceMatrix = mult(instanceMatrix, rotate(rotParam[2], 0, 0, 1));
    instanceMatrix = mult(instanceMatrix, scalem(dimenParam[0], dimenParam[1], dimenParam[0]));

    gl.uniformMatrix4fv( modelViewMatrixLoc, false, flatten(instanceMatrix) );

    normalMatrix = transpose(inverseMat3(flatten(instanceMatrix))); 

/*    
    var prepNormalMatrix = rotate(rotParam[0], 1, 0, 0);
    prepNormalMatrix = mult(prepNormalMatrix, rotate(rotParam[1], 0, 1, 0));
    prepNormalMatrix = mult(prepNormalMatrix, rotate(rotParam[2], 0, 0, 1));
    
    normalMatrix = transpose(inverseMat3(flatten(prepNormalMatrix))); 
*/    
    gl.uniformMatrix3fv(normalMatrixLoc, false, flatten(normalMatrix) );

    ambientProduct = mult(lightAmbient, mult( colours[colourParam], materialAmbient) );
    diffuseProduct = mult(lightDiffuse, mult( colours[colourParam], materialDiffuse) );
    specularProduct = mult(lightSpecular, materialSpecular);

    gl.uniform4fv( ambientProductLoc, flatten(ambientProduct) );
    gl.uniform4fv( diffuseProductLoc, flatten(diffuseProduct) );
    gl.uniform4fv( specularProductLoc, flatten(specularProduct) );
    gl.uniform1f( shininessLoc, materialShininess );
    

    for( var i=0; i<indexParam; i+=3) {
        gl.drawArrays( gl.TRIANGLES, i, 3 );
    }
}


// FOLLOWING FROM A POST, not sure if will use it, it seemed not to do
// what was expected
/**
 * @fileoverview gl-matrix - High performance matrix and vector operations
 * @author Brandon Jones
 * @author Colin MacKenzie IV
 * @version 2.3.1
 */
/* Copyright (c) 2015, Brandon Jones, Colin MacKenzie IV.

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
THE SOFTWARE. */

// modified from gl-matrix.js 
function inverseMat3(a) {
    var a00 = a[0], a01 = a[1], a02 = a[2], a03 = a[3],
        a10 = a[4], a11 = a[5], a12 = a[6], a13 = a[7],
        a20 = a[8], a21 = a[9], a22 = a[10], a23 = a[11],
        a30 = a[12], a31 = a[13], a32 = a[14], a33 = a[15],

        b00 = a00 * a11 - a01 * a10,
        b01 = a00 * a12 - a02 * a10,
        b02 = a00 * a13 - a03 * a10,
        b03 = a01 * a12 - a02 * a11,
        b04 = a01 * a13 - a03 * a11,
        b05 = a02 * a13 - a03 * a12,
        b06 = a20 * a31 - a21 * a30,
        b07 = a20 * a32 - a22 * a30,
        b08 = a20 * a33 - a23 * a30,
        b09 = a21 * a32 - a22 * a31,
        b10 = a21 * a33 - a23 * a31,
        b11 = a22 * a33 - a23 * a32,

        // Calculate the determinant
        det = b00 * b11 - b01 * b10 + b02 * b09 + b03 * b08 - b04 * b07 + b05 * b06;

    if (!det) { 
        return null; 
    }
    det = 1.0 / det;

    var out = mat3();
    out[0][0] = (a11 * b11 - a12 * b10 + a13 * b09) * det;
    out[1][1] = (a12 * b08 - a10 * b11 - a13 * b07) * det;
    out[2][2] = (a10 * b10 - a11 * b08 + a13 * b06) * det;

    out[1][0] = (a02 * b10 - a01 * b11 - a03 * b09) * det;
    out[1][1] = (a00 * b11 - a02 * b08 + a03 * b07) * det;
    out[1][2] = (a01 * b08 - a00 * b10 - a03 * b06) * det;

    out[2][0] = (a31 * b05 - a32 * b04 + a33 * b03) * det;
    out[2][1] = (a32 * b02 - a30 * b05 - a33 * b01) * det;
    out[2][2] = (a30 * b04 - a31 * b02 + a33 * b00) * det;

    return out;
}

