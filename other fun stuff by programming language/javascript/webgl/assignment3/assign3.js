
// TODO
// add eye rotation to include up/down
// do the nicer sphere
// nice to haves
//   perspective - trying but doesn't look good
//   lighting
var worldCanvas;
var worldGl;
var colourCanvas;
var colourGl;

var numTimesToSubdivide = 4;
 
var index = 0;

var pointsArray = [];

var near = -10;
var far = 10;
var radius = 6.0;
var theta  = 0.0;
var phi    = 0.0;
var dr = 5.0 * Math.PI/180.0;

var left = -2.0;
var right = 2.0;
var ytop = 2.0;
var bottom = -2.0;

var  aspect;       // Viewport aspect ratio (for perspective)

var modelViewMatrix, newObjScaleMatrix;
var modelViewMatrixLoc, projectionMatrixLoc, newObjScaleMatrixLoc, newObjColourVectorLoc;
var projectionMatrix = ortho(left, right, bottom, ytop, near, far);

var eye;
const at = vec3(0.0, 0.2, 0.0); // looks down a bit at the scene
const up = vec3(0.0, 1.0, 0.0);

var vLocation, vLocationLoc, vRotation, vRotationLoc;
var theta = [ 0, 0, 0 ];

var WORLD_SCALE = .2;
var objectScaleMatrix;
var objectScaleMatrixLoc, objectColourVectorLoc;

var worldProjectionMatrix, worldProjectionMatrixLoc;

var mouseDown = false;
var lastMouseX = null;
var lastMouseY = null;

var worldRotationMatrix = mat4();
var worldRotationMatrixLoc;
var worldRotationDeltaX = 0.1;
var worldRotationDeltaY = 0.0;

var worldShapePointsArrays = [];
var worldShapeIndexArray = [];
var worldShapeLocationArrays = [];
var worldShapeRotationArrays = [];
var worldShapeDimensionArrays = [];
var worldShapeColourArrays = [];
var worldShapeWireFrameArrays = [];

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

var cindex = 1;
var black = flatten(colours[0]);
var green = flatten(colours[4]);
var blue = flatten(colours[5]);
var red = flatten(colours[2]);

var vOrigin = flatten(translate(0.0, 0.0, 0.0));
var vNoRotation = vec3(0.0, 0.0, 0.0);
var axes = flatten([ vec4(-100.0, 0.0, 0.0, 1.0), vec4(100.0, 0.0, 0.0, 1.0), 
                     vec4(0.0, -100.0, 0.0, 1.0), vec4(0.0, 100.0, 0.0, 1.0), 
                     vec4(0.0, 0.0, -100.0, 1.0), vec4(0.0, 0.0, 100.0, 1.0) ]);
var worldScale = flatten(scalem(WORLD_SCALE, WORLD_SCALE, WORLD_SCALE));

function triangle(a, b, c) {
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
    // worldCanvas is for displaying all the objects together
    worldCanvas = document.getElementById( "gl-world-canvas" );

    worldGl = WebGLUtils.setupWebGL( worldCanvas );
    if ( !worldGl ) { alert( "WebGL isn't available" ); }

    worldGl.viewport( 0, 0, worldCanvas.width, worldCanvas.height );
    worldGl.clearColor( .9, .9, .9, 1.0 );


    // this isn't working well at the moment not using perspective view
    aspect = worldCanvas.width/worldCanvas.height;
    var  fovy = 60.0;  // Field-of-view in Y direction angle (in degrees)
    worldProjectionMatrix = flatten(perspective(fovy, aspect, -10.0, 10.0));

    initColourPicker();

    //
    //  Load shaders and initialize attribute buffers
    //
    var worldProgram = initShaders( worldGl, "vertex-world-shader", "fragment-world-shader" );
    worldGl.useProgram( worldProgram );
    
    initShapeOption("sphere");
    initWorldPoints(worldProgram);
    
    document.getElementById("newbtn").onclick = function() {
        
        // the current shape is only in the points array so far, now it has to be added to the shapes arrays
        
        worldShapePointsArrays.push(pointsArray);
        worldShapeIndexArray.push(index);
        worldShapeLocationArrays.push([ parseFloat(document.getElementById("xLocSlider").value) * WORLD_SCALE,
            parseFloat(document.getElementById("yLocSlider").value) * WORLD_SCALE,
            parseFloat(document.getElementById("zLocSlider").value) * WORLD_SCALE ]);
            
        worldShapeRotationArrays.push([ parseFloat(document.getElementById("xRotSlider").value),
            parseFloat(document.getElementById("yRotSlider").value),
            parseFloat(document.getElementById("zRotSlider").value) ]);

        dimenParam = [ parseFloat(document.getElementById("radSlider").value) * .3,
            parseFloat(document.getElementById("hSlider").value) * .3 ];
            
        if (document.getElementById("shape").value = "sphere") {
            dimenParam[1] = dimenParam[0]; // sphere doesn't use height
        }
        
        worldShapeDimensionArrays.push(dimenParam);
        worldShapeColourArrays.push(cindex);
        worldShapeWireFrameArrays.push(document.getElementById("wireframe").checked);
        
        // default the inputs, and the shape to sphere again
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
        document.getElementById("wireframe").checked = true;
        
        index = 0;
        cindex = 1;
        pointsArray = []; 
        init();
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

    render();
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
    var NFACETS = 20;
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
function initWorldPoints(worldProgram) {

    worldGl.enable(worldGl.DEPTH_TEST);
    worldGl.depthFunc(worldGl.LEQUAL);
    worldGl.enable(worldGl.POLYGON_OFFSET_FILL);
    worldGl.polygonOffset(1.0, 2.0);

    vLocationLoc = worldGl.getUniformLocation( worldProgram, "vLocation" );    
    thetaLoc = worldGl.getUniformLocation(worldProgram, "theta");

    objectScaleMatrixLoc = worldGl.getUniformLocation( worldProgram, "objectScaleMatrix" );
    worldRotationMatrixLoc = worldGl.getUniformLocation( worldProgram, "worldRotationMatrix" );    
    objectColourVectorLoc = worldGl.getUniformLocation( worldProgram, "objectColourVector" );
    worldProjectionMatrixLoc = worldGl.getUniformLocation( worldProgram, "worldProjectionMatrix" );

    var vBuffer = worldGl.createBuffer();
    worldGl.bindBuffer( worldGl.ARRAY_BUFFER, vBuffer);
    
    var vPosition = worldGl.getAttribLocation( worldProgram, "vPosition");
    worldGl.vertexAttribPointer( vPosition, 4, worldGl.FLOAT, false, 0, 0);
    worldGl.enableVertexAttribArray( vPosition);        
    
    worldCanvas.onmousedown = handleMouseDown;
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
    worldRotationDeltaX += deltaX / 10;

    var deltaY = newY - lastMouseY; // not using this at the moment
    worldRotationDeltaY += deltaY / 10;

    lastMouseX = newX
    lastMouseY = newY;
}

function render() {
    
    renderWorldCanvas();
    window.requestAnimFrame(render);
}

function renderWorldCanvas() {
    
    worldGl.clear( worldGl.COLOR_BUFFER_BIT | worldGl.DEPTH_BUFFER_BIT);

    // the mouse drag inc/decrements the x rotation of the whole world (moves the eye)
    eye = vec3(Math.sin(worldRotationDeltaX)*Math.cos(phi), 
        Math.sin(worldRotationDeltaX)*Math.sin(phi), Math.cos(worldRotationDeltaX));
    rotMatrix = lookAt(eye, at , up);
    worldGl.uniformMatrix4fv( worldRotationMatrixLoc, false, flatten(rotMatrix) );
    worldGl.uniformMatrix4fv( worldProjectionMatrixLoc, false, flatten(projectionMatrix));// worldProjectionMatrix );

    renderAxesToWorld();

    // the current object is not in the world yet
    locParam = [ parseFloat(document.getElementById("xLocSlider").value) * WORLD_SCALE,
        parseFloat(document.getElementById("yLocSlider").value) * WORLD_SCALE,
        parseFloat(document.getElementById("zLocSlider").value) * WORLD_SCALE ];
        
    rotParam = [ parseFloat(document.getElementById("xRotSlider").value),
        parseFloat(document.getElementById("yRotSlider").value),
        parseFloat(document.getElementById("zRotSlider").value) ];

    dimenParam = [ parseFloat(document.getElementById("radSlider").value) * .3,
        parseFloat(document.getElementById("hSlider").value) * .3 ];
        
    if (document.getElementById("shape").value == "sphere") {
        dimenParam[1] = dimenParam[0]; // sphere doesn't use height
    }
    
    renderShapeToWorld(pointsArray, index, locParam, rotParam, dimenParam, cindex, document.getElementById("wireframe").checked);    

    // draw all the other shapes already added to the world
    for (var i = 0; i < worldShapePointsArrays.length; i++) {
        renderShapeToWorld(worldShapePointsArrays[i], worldShapeIndexArray[i], worldShapeLocationArrays[i], 
        worldShapeRotationArrays[i], worldShapeDimensionArrays[i], worldShapeColourArrays[i],
        worldShapeWireFrameArrays[i]);    
    }
}

function renderShapeToWorld(pointsParam, indexParam, locParam, rotParam, dimenParam, colourParam, wireframeParam) {
    
    objectScaleMatrix = scalem(WORLD_SCALE * dimenParam[0], WORLD_SCALE * dimenParam[1], WORLD_SCALE * dimenParam[0]);
    worldGl.uniformMatrix4fv( objectScaleMatrixLoc, false, flatten(objectScaleMatrix) );

    worldGl.bufferData( worldGl.ARRAY_BUFFER, flatten(pointsParam), worldGl.STATIC_DRAW);
    vLocation = translate(locParam[0] / dimenParam[0], locParam[1] / dimenParam[1], locParam[2] / dimenParam[0]);

    worldGl.uniformMatrix4fv( vLocationLoc, false, flatten(vLocation) );
    worldGl.uniform3fv(thetaLoc, rotParam);

    var mainColour = flatten(colours[colourParam]);

    for( var i=0; i<indexParam; i+=3) {
        worldGl.uniform4fv( objectColourVectorLoc, mainColour );
        worldGl.drawArrays( worldGl.TRIANGLES, i, 3 );
        if (wireframeParam) {
            worldGl.uniform4fv( objectColourVectorLoc, black );
            worldGl.drawArrays( worldGl.LINE_LOOP, i, 3 );
        }
    }
}

function renderAxesToWorld() {
    
    worldGl.uniformMatrix4fv( objectScaleMatrixLoc, false, worldScale );
    worldGl.uniformMatrix4fv( vLocationLoc, false, vOrigin );
    worldGl.uniform3fv(thetaLoc, vNoRotation);

    worldGl.bufferData( worldGl.ARRAY_BUFFER, axes, worldGl.STATIC_DRAW);

    worldGl.uniform4fv( objectColourVectorLoc, green );
    worldGl.drawArrays( worldGl.LINES, 0, 2 );
    worldGl.uniform4fv( objectColourVectorLoc, blue );
    worldGl.drawArrays( worldGl.LINES, 2, 2 );
    worldGl.uniform4fv( objectColourVectorLoc, red );
    worldGl.drawArrays( worldGl.LINES, 4, 2 );
}
