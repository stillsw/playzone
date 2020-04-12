"use strict";

var canvas;
var gl;

var maxNumVertices = 5000;

var colors = [

    vec4( 0.0, 0.0, 0.0, 1.0 ),  // black
    vec4( 1.0, 1.0, 1.0, 1.0 ),  // white
    vec4( 1.0, 0.0, 0.0, 1.0 ),  // red
    vec4( 1.0, 1.0, 0.0, 1.0 ),  // yellow
    vec4( 0.0, 1.0, 0.0, 1.0 ),  // green
    vec4( 0.0, 0.0, 1.0, 1.0 ),  // blue
    vec4( 1.0, 0.0, 1.0, 1.0 ),  // magenta
    vec4( 0.0, 1.0, 1.0, 1.0)   // cyan
];    

var isMouseDown = false;
var t;
var numLines = 0;
var numIndices = [];
numIndices[0] = 0;
var start = [0];
var currLineWidth = 1;
var widths = [currLineWidth];
var index = 0;
var cindex = 0;
var cSelectorHeight;

function init() {
    
    canvas = document.getElementById( "gl-canvas" );

    gl = WebGLUtils.setupWebGL( canvas );
    if ( !gl ) { alert( "WebGL isn't available" ); }

    var program = initShaders( gl, "vertex-shader", "fragment-shader" );
    gl.useProgram( program );

    // event listeners

    canvas.addEventListener("click", function(event) {
	var rect = canvas.getBoundingClientRect();
	
	if ((event.clientY-rect.top) > canvas.height - cSelectorHeight) {
	    cindex = parseInt((event.clientX-rect.left) / (canvas.width / colors.length));
	    //console.log("colour selection click at x/y="+event.clientX+"/"+event.clientY+" index="+cindex+" offsets left/top="+canvas.offsetLeft+"/"+canvas.offsetTop); 
	}
    });
    
    canvas.addEventListener("mousemove", function (event) {
        if (isMouseDown) {
	    var rect = canvas.getBoundingClientRect();
	    
	    if ((event.clientY-rect.top) >= canvas.height-cSelectorHeight) {
		endLine(event);
	    }
	    else {
		
		// add another vertex to the current line
		t = vec2(2*(event.clientX-rect.left)/canvas.width-1, 
		   2*(canvas.height-(event.clientY-rect.top))/canvas.height-1);
		gl.bindBuffer( gl.ARRAY_BUFFER, bufferId );
		gl.bufferSubData(gl.ARRAY_BUFFER, 8*index, flatten(t));

		t = vec4(colors[cindex]);
		
		gl.bindBuffer( gl.ARRAY_BUFFER, cBufferId );
		gl.bufferSubData(gl.ARRAY_BUFFER, 16*index, flatten(t));

		numIndices[numLines-1]++;
		widths[numLines-1] = currLineWidth;
		index++;
		render();
		
		if (index >= maxNumVertices) { // buffer full, end the line now
		    endLine(event);
		}		
	    }
        }
    });

    canvas.addEventListener("mousedown", function (event) {

	if (index >= maxNumVertices) { // buffer full, can do no more lines
	    alert("Run out of buffer space");
	}		
	
	else if (!isMouseDown) { // start a new line only if not already drawing one
            isMouseDown = true;
	    numLines++;
	    numIndices[numLines-1] = 0;
	    start[numLines-1] = index;
	}
    });
    
    canvas.addEventListener("mouseup", function (event) {
	endLine(event);
    });

    canvas.addEventListener("mouseleave", function (event) {
        endLine(event);
    });

    document.getElementById("slider").onchange = function() {
        currLineWidth = event.target.value;
	widths[numLines] = currLineWidth;
    };
    
    gl.viewport( 0, 0, canvas.width, canvas.height );
    gl.clearColor( 0.8, 0.8, 0.8, 1.0 );
    gl.clear( gl.COLOR_BUFFER_BIT );


    //
    //  Load shaders and initialize attribute buffers
    //
    var program = initShaders( gl, "vertex-shader", "fragment-shader" );
    gl.useProgram( program );
    
    var bufferId = gl.createBuffer();
    gl.bindBuffer( gl.ARRAY_BUFFER, bufferId );
    gl.bufferData( gl.ARRAY_BUFFER, 8*maxNumVertices, gl.STATIC_DRAW );
    var vPos = gl.getAttribLocation( program, "vPosition" );
    gl.vertexAttribPointer( vPos, 2, gl.FLOAT, false, 0, 0 );
    gl.enableVertexAttribArray( vPos );
    
    var cBufferId = gl.createBuffer();
    gl.bindBuffer( gl.ARRAY_BUFFER, cBufferId );
    gl.bufferData( gl.ARRAY_BUFFER, 16*maxNumVertices, gl.STATIC_DRAW );
    var vColor = gl.getAttribLocation( program, "vColor" );
    gl.vertexAttribPointer( vColor, 4, gl.FLOAT, false, 0, 0 );
    gl.enableVertexAttribArray( vColor );

    // init the colour selector boxes
    
    var bw = canvas.width / colors.length;
    cSelectorHeight = canvas.height / 20; // boxes area at the bottom is 1/20 canvas height
    var by = cSelectorHeight;
    for (var i = 0; i < colors.length; i++) {
	var bx = bw * i;
	// add 4 points for this colour
	for (var j = 0; j < 4; j++) {
	    var px = (j == 0 || j == 3 ? bx : bx + bw);
	    var py = (j < 2 ? 0 : by);
	    var bp = vec2(2*px/canvas.width-1, 2*(py)/canvas.height-1);
	    gl.bindBuffer( gl.ARRAY_BUFFER, bufferId );
	    gl.bufferSubData(gl.ARRAY_BUFFER, 8*index, flatten(bp));
	    
	    t = vec4(colors[i]);
	    
	    gl.bindBuffer( gl.ARRAY_BUFFER, cBufferId );
	    gl.bufferSubData(gl.ARRAY_BUFFER, 16*index, flatten(t));	    
	    index++;
	}
    } 

    render();
};

function endLine(event) {
    if (isMouseDown) {
        // end line, called when mouse leaves the canvas drawing area and on mouse up
	// also if mouse into the colour selector
        isMouseDown = false;        
    }
}

window.onload = init;

function render() {
    gl.clear( gl.COLOR_BUFFER_BIT );

    // draw lines
    
    for(var i=0; i<numLines; i++) {
	gl.lineWidth(widths[i]);
        gl.drawArrays( gl.LINE_STRIP, start[i], numIndices[i] );
    }

    // draw colour selector
    
    for(var i=0; i<colors.length; i++) { 
        gl.drawArrays( gl.TRIANGLE_FAN, 4*i, 4 );
    }
    
}
