var canvas;
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

var cindex = 4, light1Cidx = 1, light2Cidx = 2;

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
    document.getElementById("hSlider").value = 3; // height
    document.getElementById("hSlider").disabled = true;
    document.getElementById("hPrompt").style.color = 'grey';
    document.getElementById("shape").value = "sphere";
    
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
    else if (shape == "cylinder") {
        makeCylinder(1);
    }
    else if (shape == "cone") {
        makeCone(1);
    }
    else {
        alert("init shape todo "+shape);
    }
}

function makeCylinder(r1) { 
    
    var TWOPI = Math.PI * 2.0;
    var NFACETS = 30;
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
        
        // points around the circumference of circle with P1 at the centre
        q[n][x] = P1[x] + r1 * Math.cos(theta1) * A[x] + r1 * Math.sin(theta1) * B[x];
        q[n][y] = P1[y] + r1 * Math.cos(theta1) * A[y] + r1 * Math.sin(theta1) * B[y];
        q[n][z] = P1[z] + r1 * Math.cos(theta1) * A[z] + r1 * Math.sin(theta1) * B[z];

        // P2 at the centre
        n = 1;
        q[n] = vec4(0, 0, 0, 1);
        q[n][x] = P2[x] + r1 * Math.cos(theta1) * A[x] + r1 * Math.sin(theta1) * B[x];
        q[n][y] = P2[y] + r1 * Math.cos(theta1) * A[y] + r1 * Math.sin(theta1) * B[y];
        q[n][z] = P2[z] + r1 * Math.cos(theta1) * A[z] + r1 * Math.sin(theta1) * B[z];

        // top face
        n = 2;
        q[n] = vec4(0, 0, 0, 1);
        q[n][x] = P1[x] + r1 * Math.cos(theta2) * A[x] + r1 * Math.sin(theta2) * B[x];
        q[n][y] = P1[y] + r1 * Math.cos(theta2) * A[y] + r1 * Math.sin(theta2) * B[y];
        q[n][z] = P1[z] + r1 * Math.cos(theta2) * A[z] + r1 * Math.sin(theta2) * B[z];

        // bottom face
        n = 3;
        q[n] = vec4(0, 0, 0, 1);
        q[n][x] = P2[x] + r1 * Math.cos(theta2) * A[x] + r1 * Math.sin(theta2) * B[x];
        q[n][y] = P2[y] + r1 * Math.cos(theta2) * A[y] + r1 * Math.sin(theta2) * B[y];
        q[n][z] = P2[z] + r1 * Math.cos(theta2) * A[z] + r1 * Math.sin(theta2) * B[z];
        
        // facing a facet the points are arranged like this:
        //    q0-------q2
        //    |       / |
        //    |      /  |
        //    |     /   |
        //    |    /    |
        //    |   /     |
        //    |  /      |
        //    | /       |
        //    q1-------q3 
        
        // top face triangle, normal is in the same direction as the centre
        var topNormal = vec4(0, 1, 0, 1);
        var bottomNormal = vec4(0, -1, 0, 1);
        pointsArray.push(P1);
        pointsArray.push(q[0]);
        pointsArray.push(q[2]);
        normalsArray.push(topNormal);
        normalsArray.push(topNormal);
        normalsArray.push(topNormal);
        index += 3;

        pointsArray.push(q[2]);
        // the normals are the displacement on the x and z axes, the following formula
        // calculates them, but it's not needed here
        // let q = point, pa = origin, va = unit vector along axis through cylinder (ie. P1),  
        //[(q – pa) - (va . (q – pa) ) va)]normalized.
        //var nv1 = vec4(q[2][x], 0, q[2][z], 1);
        //var vToPoint = subtract( nv1, topNormal );
        //var vaMultVToPoint = mult( topNormal, vToPoint ); // topNormal is equiv va as it's len = 1
        //var nv2 = subtract( vToPoint, mult( vaMultVToPoint, topNormal) );
        normalsArray.push(vec4(q[2][x], 0, q[2][z], 1));
        pointsArray.push(q[0]);
        normalsArray.push(vec4(q[0][x], 0, q[0][z], 1));
        pointsArray.push(q[1]);
        normalsArray.push(vec4(q[1][x], 0, q[1][z], 1));
        index += 3;

        pointsArray.push(q[3]);
        normalsArray.push(vec4(q[3][x], 0, q[3][z], 1));
        pointsArray.push(q[2]);
        normalsArray.push(vec4(q[2][x], 0, q[2][z], 1));
        pointsArray.push(q[1]);
        normalsArray.push(vec4(q[1][x], 0, q[1][z], 1));
        index += 3;

        // bottom face (winding is reverse of top, since points downwards)
        pointsArray.push(P2);
        pointsArray.push(q[3]);
        pointsArray.push(q[1]);
        normalsArray.push(bottomNormal);
        normalsArray.push(bottomNormal);
        normalsArray.push(bottomNormal);
        index += 3;
    }

    // average the normals... this mucks it up probably because
    // the points are repeated in each face they appear, so not easy to create
    // an average... need to have a point averaging over the faces it appears in
    // and so only one point used in several triangles
    /*
    for (var i = 0; i < normalsArray.length; i+=3) {
        var a = pointsArray[i];
        var b = pointsArray[i+1];
        var c = pointsArray[i+2];
        var t1 = subtract(b, a);
        var t2 = subtract(c, a);
        var normal = vec4( normalize(cross(t2, t1)) );
        normalsArray[i] = normalize( add(normalsArray[i], normal) );
        normalsArray[i+1] = normalize( add(normalsArray[i+1], normal) );
        normalsArray[i+2] = normalize( add(normalsArray[i+2], normal) );
    } 
    */  
}

function makeCone(r1) { // just like cylinder except no bottom face and normals calc different
    
    var TWOPI = Math.PI * 2.0;
    var NFACETS = 30;
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
        
        // points around the circumference of circle with P1 at the centre
        q[n][x] = P1[x] + r1 * Math.cos(theta1) * A[x] + r1 * Math.sin(theta1) * B[x];
        q[n][y] = P1[y] + r1 * Math.cos(theta1) * A[y] + r1 * Math.sin(theta1) * B[y];
        q[n][z] = P1[z] + r1 * Math.cos(theta1) * A[z] + r1 * Math.sin(theta1) * B[z];

        // top face
        n = 1;
        q[n] = vec4(0, 0, 0, 1);
        q[n][x] = P1[x] + r1 * Math.cos(theta2) * A[x] + r1 * Math.sin(theta2) * B[x];
        q[n][y] = P1[y] + r1 * Math.cos(theta2) * A[y] + r1 * Math.sin(theta2) * B[y];
        q[n][z] = P1[z] + r1 * Math.cos(theta2) * A[z] + r1 * Math.sin(theta2) * B[z];

        // facing a facet the points are arranged like this:
        //    q0-------q1
        //    |       /
        //    |      / 
        //    |     / 
        //    |    /  
        //    |   /   
        //    |  /    
        //    | /     
        //    P2     
        
        // top face triangle, normal is in the same direction as the centre
        var topNormal = vec4(0, 1, 0, 1);
        pointsArray.push(P1);
        pointsArray.push(q[0]);
        pointsArray.push(q[1]);
        normalsArray.push(topNormal);
        normalsArray.push(topNormal);
        normalsArray.push(topNormal);
        index += 3;
        
        var va = topNormal;
        var alpha = radians(45.0);

        pointsArray.push(q[1]);
        // vector to the point as for cylinder (ie. perpendicular to cylinder side)
        var m = vec4(q[1][x], 0, q[1][z], 1);
        var nrm1 = vec4(0, 0, 0, 1);
        // n = m cos alpha + va sin alpha (where alpha = angle of the cone side, here it's 45 because cone is 2x2)
        nrm1[x] = m[x] * Math.cos(alpha) + va[x] * Math.sin(alpha);
        nrm1[y] = m[y] * Math.cos(alpha) + va[y] * Math.sin(alpha);
        nrm1[z] = m[z] * Math.cos(alpha) + va[z] * Math.sin(alpha);
        normalsArray.push(nrm1);

        pointsArray.push(q[0]);
        m = vec4(q[0][x], 0, q[0][z], 1);
        var nrm2 = vec4(0, 0, 0, 1);
        nrm2[x] = m[x] * Math.cos(alpha) + va[x] * Math.sin(alpha);
        nrm2[y] = m[y] * Math.cos(alpha) + va[y] * Math.sin(alpha);
        nrm2[z] = m[z] * Math.cos(alpha) + va[z] * Math.sin(alpha);
        normalsArray.push(nrm2);

        pointsArray.push(P2);
        normalsArray.push( normalize( add(nrm1, nrm2) ) );
        index += 3;
    }

/*
    // average the normals... see comments in cylinder function
    for (var i = 0; i < normalsArray.length; i+=3) {
        var a = pointsArray[i];
        var b = pointsArray[i+1];
        var c = pointsArray[i+2];
        var t1 = subtract(b, a);
        var t2 = subtract(c, a);
        var normal = vec4( normalize(cross(t2, t1)) );
        normalsArray[i] = normalize( add(normalsArray[i], normal) );
        normalsArray[i+1] = normalize( add(normalsArray[i+1], normal) );
        normalsArray[i+2] = normalize( add(normalsArray[i+2], normal) );
    }   
*/ 
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
    

    for( var i=0; i<indexParam; i+=3) {
        gl.drawArrays( gl.TRIANGLES, i, 3 );
    }
}
