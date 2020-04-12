var V = [];
var E = [];
var unvisited = [];
var workingEdges = [];
var finalPath = [], finalPathEndPoints = []; // for drawing mainly

var currentVertex, currentEdge;
var target;
var infoLine = 25;
var INFO_LINE_HEIGHT = 12;
var INFINITY = 99999999999; // just a very big number
var END = INFINITY;
var DESIRED_FPS = 4;
var ANIMATION_FRAMES = DESIRED_FPS * 2;

var n = 9; // how many to create at start
var step = 0;
var working = false, done = false;
var animateSinceFrame;
var noneOverlap = true; // while building edges checks that no 2 vertices are so close they overlap and make it hard to see

function setup()
{
    createCanvas(1200,600);

    var goodGraph = false;
    
    while (!goodGraph) {
        makeStart();
        fillRandomGraph(n);             // adds new points up to n
        makeEnd();
        
        // make sure both start and end have at least one edge connecting to some other vertex
        var startConnects = false;
        var endConnects = false;
        for (var i = 1; i < E.length; i++) {
            if (E[i].v1 == V[0]) { // edges are always added with v2 as the new vertex (ie. after first)
                startConnects = true;
            }
            if (E[i].v2 == target) { // likewise end vertex is only in v2
                endConnects = true;
            }
        }
        info("good graph? start="+startConnects+" end="+endConnects+" none overlap="+noneOverlap);
        goodGraph = startConnects && endConnects && noneOverlap;
    }
}

function draw() {
    fill(255);
    noStroke();
    rect(0, 0, 800, height);

    reportStep();
    
    if (working) {
        processCurrentStep();
    }
    
    drawComplete();
}

function processCurrentStep() {
    if (currentEdge == null && workingEdges.length == 0) {
        advanceStep(); // automate... to have it be manual comment this line and uncomment the next
//        working = false;
        return;
    }
    
    else if (currentEdge == null) {
        currentEdge = workingEdges[0];
        workingEdges.splice(0, 1);
        animateSinceFrame = frameCount;
    }
    
    if (animateSinceFrame <= frameCount - ANIMATION_FRAMES) {

        var dist = currentVertex.knownDist + currentEdge.d;
        var destV = currentEdge.v1 == currentVertex ? currentEdge.v2 : currentEdge.v1; // whichever end has the destination vertex
        if (dist < destV.knownDist) {
            info("setting "+destV.name+" knownDist="+round(dist));
            destV.prevVertex = currentEdge; // actually keeping the edge is more useful later for retracing the path
            destV.knownDist = dist;
        }
        
        currentEdge.done = true;
        currentEdge = null;
    }
}

function reportStep() {
    push();
    textAlign(LEFT, TOP);
    textSize(30);
    noStroke();
    fill(0,206,209);
    if (working) {
        text("Doing step "+step+"...", 15, 10);
    }
    else if (step == 0) {
        text("Press space to start", 15, 10);
    }
    else if (done) {
        if (unvisited.length > 0 && target.knownDist == INFINITY) {
            text("Done, no path to target", 15, 10);
        }
        else {
            text("Done in "+step+" steps, distance="+round(target.knownDist)+" path="+bestPathToString(), 15, 10);
        }
    }
    else {
        text("Step "+step+" press space to advance", 15, 10);
    }
    pop();
}

function bestPathToString() {
    var path = "";
    for (var i = finalPathEndPoints.length -1; i >= 0; i--) {
        path += finalPathEndPoints[i].name;
        if (i > 0) {
            path += ", ";
        }
    }
    return path;
}

function advanceStep() {
    frameRate(DESIRED_FPS);// attempt to have 2 fps

    if (step == 0) { // initialise first
        V[0].knownDist = 0;                     // start vertex known distance to zero

        for (var i = 0; i < V.length; i++) {
            unvisited.push(V[i]);               // add all vertices to unvisited list
        }
    }

    step++;

    // get unvisisted with minimum known distance
    currentVertex = getNextVertex();
    
    if (unvisited.length == 0 || currentVertex == null) {
        done = true;
        working = false;
        buildFinalPath();
    }
    else {
        animateSinceFrame = frameCount;
        // get edges where currentVertex is an endpoint and other end is unvisited
        for (var i = 0; i < E.length; i++) {
            if ((E[i].v1 == currentVertex && unvisited.includes(E[i].v2)) || 
                (E[i].v2 == currentVertex && unvisited.includes(E[i].v1))) {
                workingEdges.push(E[i]);
            }
        }
    }    
}

function buildFinalPath() {
    if (target.knownDist < INFINITY) { // make sure the target was reached
        var nextEdge = target.prevVertex;
        var v = target;
        finalPathEndPoints.push(v);
        while (nextEdge != null) {
            v = nextEdge.v1 == v ? nextEdge.v2 : nextEdge.v1;
            finalPathEndPoints.push(v);
            finalPath.push(nextEdge);
            nextEdge = v.prevVertex;
        }
    }
}

function getNextVertex() {
    var nextV;
    var shortestKnown = INFINITY;
    
    for (var i = 0; i < unvisited.length; i++) {
        var u = unvisited[i];

        if (u.knownDist < shortestKnown) {
            shortestKnown = u.knownDist;
            nextV = u;
        }
    }
    
    if (nextV) {
        info("take "+nextV.name);
        unvisited.splice(unvisited.indexOf(nextV), 1);
    } 
    
    return nextV;
}

// making an edge from an existing vertex in v1 to a new one in v2
function makeAnEdge(v1, v2, randomChance) {
    var e = {v1: v1, v2: v2, d: round(dist(v1.x, v1.y, v2.x, v2.y)/10), done: false};
    if (e.d < 5) {
        noneOverlap = false; // will cause the graph to restart
        return;
    }

    if (!randomChance || (randomChance && random(0,100) < 40)) { // 40% chance of drawing an edge
        
        if (randomChance && e.d > 40) {
            return; // don't add edge if more than half the width (avoid getting start too close to end)
        }
        
        E.push(e);
        return e;
    }
    
}

function makeAVertex(i, x, y) {
    var v = {name: String.fromCharCode("a".charCodeAt() + i), x: x, y: y, knownDist: INFINITY, prevVertex: null};

    for (var j = 0; j < V.length; j++) {
        var e = makeAnEdge(V[j], v, true);
    }

    V.push(v); // add to the vertex array

    return v;
}

function makeStart() {
    noneOverlap = true;
    V = [];
    E = [];
    makeAVertex(0, 50, height/2);
}

function makeEnd() {
    target = makeAVertex(V.length, 750, height/2);
}

function fillRandomGraph(uptoN) { // some looks a lot like the startTriangle code above
    for (var i = 1; i < uptoN; i++) { // start at 1
        var point = {x: round(random(1, 7)) * 100, y: constrain(round(random(.2, 5.8)),1,5.8) * 100}; // points are at least 100px apart
        makeAVertex(i, point.x, point.y);
    }
}

function keyPressed() {
    if (keyCode == 32 && !working && step != END) { 
        working = true;
        advanceStep();
    }
}

function info(what) {
    push();
    noStroke();
    fill(0);
    textAlign(LEFT, BASELINE);
    text(what, 800, infoLine);
    infoLine += INFO_LINE_HEIGHT;
    pop();
}

function drawComplete() {
    push();
    fill(0);
    textAlign(CENTER, CENTER);
    textSize(12);

    for (var j = 0; j < E.length; j++) {
        if (currentEdge != null && E[j] == currentEdge) {
            strokeWeight(2);
            stroke((frameCount - animateSinceFrame) % 2 == 0 ? "orange" : "black");
        }
        else if (finalPath.includes(E[j])) {
            strokeWeight(4);
            stroke(255, 0, 255);
        }
        else if (E[j].done) {
            strokeWeight(2);
            stroke("black");
        }
        else {
            strokeWeight(1);
            stroke(0, 0, 0, 50);
        }
        line(E[j].v1.x, E[j].v1.y, E[j].v2.x, E[j].v2.y);
        var midX = E[j].v1.x - (E[j].v1.x - E[j].v2.x) / 2;
        var midY = E[j].v1.y - (E[j].v1.y - E[j].v2.y) / 2;
        noStroke();
        text(round(E[j].d), midX, midY);
    }
        
    strokeWeight(2);
    
    for (var i = 0; i < V.length; i++) {
        if (currentEdge != null && (currentEdge.v1 == V[i] || currentEdge.v2 == V[i])) {
            stroke((frameCount - animateSinceFrame) % 2 == 0 ? "orange" : "black");
        }
        else if (finalPathEndPoints.includes(V[i])) {
            strokeWeight(4);
            stroke(255, 0, 255);
        }
        else {
            stroke(0);
        }
        if (step == 0 || unvisited.includes(V[i])) {
            fill("yellow");
        }
        else if (finalPathEndPoints.includes(V[i])) {
            fill(255);
        }
        else {
            fill("orange");
        }
        ellipse(V[i].x, V[i].y, 30);
        noStroke();
        fill(0);
        textSize(20);
        text(V[i].name, V[i].x, V[i].y);
        if (i > 0) {
            textSize(12);
            text(V[i].knownDist == INFINITY ? "?" : round(V[i].knownDist), V[i].x +20, V[i].y -20);
        }
    }    
    
    pop();
}
