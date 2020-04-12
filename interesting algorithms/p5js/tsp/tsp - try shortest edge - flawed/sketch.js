var V = [];
var E = [];
var shortestCycle = [];
var infoLine = 25;
var INFO_LINE_HEIGHT = 12;

var n = 4; // how many to create at start

function setup()
{
    createCanvas(1200,600);
    
    makeStartTriangle([]);          // needs to be 3 points in object format (see getRandomPoint())
    fillRandomGraph(n);             // adds new points up to n
    drawComplete();                 // draws the complete graph in faint lines
    drawShortestCycle("red", 2);
}

// making an edge from an existing vertex in v1 to a new one in v2
// important optimisation is that the currently adding vertex needs to assess lengths to every vertex
// by examining edges, so as well as storing the distance on the edge itself, also put that onto the target
// vertex as a current distance (ie. to this vertex being added)
function makeAnEdge(v1, v2) {
    var e = {v1: v1, v2: v2, d: dist(v1.x, v1.y, v2.x, v2.y)};
    v1.currentVertexDistance = e.d;
    E.push(e);
    return e;
}

function makeAVertex(i, x, y, addEdgesToShortestCycle) {
    var v = {name: String.fromCharCode("a".charCodeAt() + i), x: x, y: y, edges: [], currentVertexDistance: -1}; // see comments to makeAnEdge()

    for (var j = 0; j < V.length; j++) { // create an edge to each vertex already existing
        var e = makeAnEdge(V[j], v);
        v.edges.push(e);            

        if (addEdgesToShortestCycle) { // because called from start triangle add all edges to the shortest cycle
            shortestCycle.push(e);
        }
    }

    V.push(v); // add to the vertex array (nb. after adding to the existing v's in the loop)

    return v;
}

function getRandomPoint() {
    return {x: random(20, 800), y: random(20, height-20)};
}

// can seed it to start the same way, or leave array empty
function makeStartTriangle(startPoints) {

    if (startPoints.length == 0) {
        for (var i = 0; i < 3; i++) {
            startPoints.push(getRandomPoint());
        }
    }
    else {
        info("use seed points "+startPoints); 
    }

    for (var i = 0; i < 3; i++) { // hard code 3, triangles can't be bigger
        makeAVertex(i, startPoints[i].x, startPoints[i].y, true);
    }
    
//    drawShortestCycle("yellow", 6);
}

function addARandomPoint(i) {
    var point = getRandomPoint();
    var v = makeAVertex(i, point.x, point.y, false);

    drawShortestCycle("yellow", 2);
    replaceWhereShortest(v);
}

function fillRandomGraph(uptoN) { // some looks a lot like the startTriangle code above
    for (var i = 3; i < uptoN; i++) { // start at 3 since already made the triangle
        addARandomPoint(i);
    }
}

function replaceWhereShortest(newVertex) {

    var shortestEdge;
    var shortestDifference = 9999999; // big
    
    // find the edge on the current shortest cycle which adds the smallest amount to the cycle
    for (var i = 0; i < shortestCycle.length; i++) {
        var diff = shortestCycle[i].v1.currentVertexDistance + shortestCycle[i].v2.currentVertexDistance - shortestCycle[i].d;
        if (diff < shortestDifference) {
            shortestDifference = diff;
            shortestEdge = shortestCycle[i];
        }
    }

    info("put "+newVertex.name+" between "+shortestEdge.v1.name+" and "+shortestEdge.v2.name);
    info("    prev distance = "+round(shortestEdge.d));
    info("    new distance = "+round(shortestEdge.v1.currentVertexDistance + shortestEdge.v2.currentVertexDistance)+
         "("+round(shortestEdge.v1.currentVertexDistance)+" to "+shortestEdge.v1.name+
         " and "+round(shortestEdge.v2.currentVertexDistance)+" to "+shortestEdge.v2.name+")");

    // remove that edge from the shortest cycle
    var pos = shortestCycle.indexOf(shortestEdge);
    shortestCycle.splice(pos, 1);
    
    // just need to add the edges from the new vertex to the shortest cycle that go to endpoints of that shortest edge
    for (var k = 0; k < newVertex.edges.length; k++) {
        if (shortestEdge.v1 == newVertex.edges[k].v1 || shortestEdge.v2 == newVertex.edges[k].v1) {
            shortestCycle.push(newVertex.edges[k]);
        }
    }
}

function keyPressed() {
    if (keyCode == 32) { // space to add a vertex
        fill(255);
        noStroke();
        rect(0, 0, 800, height);
        addARandomPoint(V.length);
        drawComplete();
        drawShortestCycle("red", 2);
   }
}

function info(what) {
    push();
    noStroke();
    fill(0);
    text(what, 800, infoLine);
    infoLine += INFO_LINE_HEIGHT;
    pop();
}

function drawComplete() {
    strokeWeight(1);
    stroke(0, 0, 0, 50);
    fill(0);
    
    for (var i = 0; i < V.length; i++) {
        text(V[i].name, V[i].x - 20, V[i].y - 5);
        ellipse(V[i].x, V[i].y, 3);
    } 
    
//    info("edges:");
    for (var j = 0; j < E.length; j++) {
        line(E[j].v1.x, E[j].v1.y, E[j].v2.x, E[j].v2.y);
//        info("edge "+E[j].v1.name+" to "+E[j].v2.name+" d="+round(E[j].d));
    }
}

function drawShortestCycle(colour, weight) {

    stroke(colour);
    strokeWeight(weight);
    for (var i = 0; i < shortestCycle.length; i++) {
//        console.log("drawshortest cycle "+i+" edge="+shortestCycle[i]);
        line(shortestCycle[i].v1.x, shortestCycle[i].v1.y, shortestCycle[i].v2.x, shortestCycle[i].v2.y);
//        line(shortestCycle[i].e2.v1.x, shortestCycle[i].e2.v1.y, shortestCycle[i].e2.v2.x, shortestCycle[i].e2.v2.y);
    }
}

