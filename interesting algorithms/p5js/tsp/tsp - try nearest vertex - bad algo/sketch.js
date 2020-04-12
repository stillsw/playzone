var V = [];
var E = [];
var shortestCycle = [];
var infoLine = 25;
var INFO_LINE_HEIGHT = 12;

var n = 4; // how many to create

function setup()
{
    createCanvas(1200,600);
    
    fillRandomGraph();
    drawComplete();
//    findSmallestTriangle();
    drawShortestCycle("red", 2);
}

/*
function findSmallestTriangle() {
    // will be quicker to order the list of distances first I guess... but for so few vertices...
    

    var shortestDist = 9999999; // just make it max possible
    var shortestEdge1, shortestEdge2;

    // traverse edges backwards because each vertex added to the end adds an edge for every existing vertex, which
    // means the previous edges won't deal with that vertex again
    
    for (var j = E.length -1; j >= 0; j++) {
        var d = dist(E[j].v1.x, E[j].v1.y, E[j].v2.x, E[j].v2.y);
        if (d < shortestDist1) {
            shortestDist1 = d;
            shortestEdge1 = E[j];
        }
        else if (d < shortestDist2) {
            shortestDist2 = d;
            shortestEdge2 = E[j];
        }
    }

    if (shortestEdge1 != null && shortestEdge2 != null) {
        shortestCycle.push({e1: shortestEdge1, e2: shortestEdge2});
    }
    
    
//    for (var i = 0; i < V.length; i++) {
//        var shortestDist1 = 99999; // just make it max possible
//        var shortestDist2 = 99999; // just make it max possible
//        var shortestEdge1, shortestEdge2;
//        
//        for (var j = 0; j < E.length; j++) {
//            if (V[i] == E[j].v1) {
//                var d = dist(E[j].v1.x, E[j].v1.y, E[j].v2.x, E[j].v2.y);
//                if (d < shortestDist1) {
//                    shortestDist1 = d;
//                    shortestEdge1 = E[j];
//                }
//                else if (d < shortestDist2) {
//                    shortestDist2 = d;
//                    shortestEdge2 = E[j];
//                }
//            }
//        }
//        
//        if (shortestEdge1 != null && shortestEdge2 != null) {
//            shortestCycle.push({e1: shortestEdge1, e2: shortestEdge2});
//        }
//    } 
}
*/

function drawShortestCycle(colour, weight) {

    stroke(colour);
    strokeWeight(weight);
    for (var i = 0; i < shortestCycle.length; i++) {
        console.log("drawshortest cycle "+i+" edge="+shortestCycle[i]);
        line(shortestCycle[i].v1.x, shortestCycle[i].v1.y, shortestCycle[i].v2.x, shortestCycle[i].v2.y);
//        line(shortestCycle[i].e2.v1.x, shortestCycle[i].e2.v1.y, shortestCycle[i].e2.v2.x, shortestCycle[i].e2.v2.y);
    }
}

function fillRandomGraph() {
    for (var i = 0; i < n; i++) {
        var ve = [];
        var v = {name: 'v'+i, x: random(20, 800), y: random(20, height-20), edges: ve};

        for (var j = 0; j < V.length; j++) {                // create an edge to each vertex already existing
            var e = {v1: V[j], v2: v, d: dist(V[j].x, V[j].y, v.x, v.y)};
            E.push(e);            
            ve.push(e);
            
            // for the 1st 3 vertices add their edges to the shortest cycle
            if (i < 3) {
                shortestCycle.push(e);
                
                if (i == 2) { // last one, draw the triangle
                    drawShortestCycle("yellow", 6);
                }
            }
        }
        
        V.push(v); // add to the vertex array (nb. after adding to the existing v's in the loop)

        if (i > 2) { // now adding another vertex, time to start finding ways to keep it shortest
            drawShortestCycle("green", 2);
            connectToNearestInCycle(v);
        }
    }
}

function connectToNearestInCycle(newVertex) {

    var shortestEdge;
    
    // find the shortest edge to connect the new vertex to the current shortest cycle
    for (var k = 0; k < newVertex.edges.length; k++) {
        if (shortestEdge == null || shortestEdge.d > newVertex.edges[k].d) {
            shortestEdge = newVertex.edges[k];
        }
    }
     
    // which vertex is it, and what is it connected to already
    var nearestVertex = shortestEdge.v1;
    info(newVertex.name+" connects "+nearestVertex.name);

//    push();
//    strokeWeight(12); stroke(200,100,0,60);
    var edgesToSwitchCandidates = [];
    for (var i = 0; i < shortestCycle.length; i++) {
        if (shortestCycle[i].v1 == nearestVertex) {
            info(shortestCycle[i].v1.name+" prev connected(2nd) "+shortestCycle[i].v2.name+" d="+round(shortestCycle[i].d));
            edgesToSwitchCandidates.push({c: shortestCycle[i].v2, edge: shortestCycle[i]});            
//            line(shortestCycle[i].v1.x, shortestCycle[i].v1.y, shortestCycle[i].v2.x, shortestCycle[i].v2.y);
//            edgesToSwitchCandidates.push(shortestCycle[i]);            
        }
        else if (shortestCycle[i].v2 == nearestVertex) {
            info(shortestCycle[i].v2.name+" prev connected(1st) "+shortestCycle[i].v1.name+" d="+round(shortestCycle[i].d));
//            line(shortestCycle[i].v1.x, shortestCycle[i].v1.y, shortestCycle[i].v2.x, shortestCycle[i].v2.y);
            edgesToSwitchCandidates.push({c: shortestCycle[i].v1, edge: shortestCycle[i]});
//            edgesToSwitchCandidates.push(shortestCycle[i]);
        }
    }
//    pop();
//    var pos = shortestCycle.indexOf(edgesToSwitchCandidates[0]);
//    shortestCycle.splice(pos, 1);
//    pos = shortestCycle.indexOf(edgesToSwitchCandidates[1]);
//    shortestCycle.splice(pos, 1);

    // should have 2 edges and one of them is going to be replaced
    if (edgesToSwitchCandidates.length == 2) {
        var replace = -1;

        // find the distances to the 2 other endpoints from the new vertex, already stored
        for (var k = 0; k < newVertex.edges.length; k++) {
            
            if (newVertex.edges[k].v1 == edgesToSwitchCandidates[0].c) {
                edgesToSwitchCandidates[0].d = newVertex.edges[k].d;
                edgesToSwitchCandidates[0].idx = k;
            }
            else if (newVertex.edges[k].v1 == edgesToSwitchCandidates[1].c) {
                edgesToSwitchCandidates[1].d = newVertex.edges[k].d;
                edgesToSwitchCandidates[1].idx = k;
            }
        }

        // shouldn't need to calc this
//        var d0 = dist(v.x, v.y, edgesToSwitchCandidates[0].nearestVertex.x, edgesToSwitchCandidates[0].nearestVertex.y);
//        var d1 = dist(v.x, v.y, edgesToSwitchCandidates[1].nearestVertex x, edgesToSwitchCandidates[1].nearestVertex y);

        var r = -1;
        var k = -1;
        if (edgesToSwitchCandidates[0].d - edgesToSwitchCandidates[0].edge.d < edgesToSwitchCandidates[1] - edgesToSwitchCandidates[1].edge.d) {
            info(round(edgesToSwitchCandidates[0].d)+" (d0) - "+round(edgesToSwitchCandidates[0].edge.d)+" < "+round(edgesToSwitchCandidates[1].d)+" (d1) - "+round(edgesToSwitchCandidates[1].edge.d));
            r = 1;
            k = 0;
        }
        else {
            info(round(edgesToSwitchCandidates[1].d)+" (d1) - "+round(edgesToSwitchCandidates[1].edge.d)+" < "+round(edgesToSwitchCandidates[0].d)+" (d0) - "+round(edgesToSwitchCandidates[0].edge.d));
            r = 0;
            k = 1;
        }
        
        var pos = shortestCycle.indexOf(edgesToSwitchCandidates[r].edge);
        shortestCycle.splice(pos, 1);

/*
            if (newVertex.edges[k].v1 == edgesToSwitchCandidates[0].c) {
                edgesToSwitchCandidates[0].d = newVertex.edges[k].d;
                edgesToSwitchCandidates[0].idx = k;
*/
        shortestCycle.push(newVertex.edges[edgesToSwitchCandidates[r].idx]);
    }
    else {
        info("something went wrong, replace candidates len="+edgesToSwitchCandidates.length);
    }

    // now push the new edge to the cycle
    info("sometimes looks right, but not always... idk why")
    // theory: at the moment the aglo says if find nearest vertex then the best connect is to remove the existing edge to that vertex
    // which when added to the new edge needed to make the cycle will make it longer of the 2
    // BUT the maths is wrong the way it is atm... for some reason there are cases when it takes the wrong edge away
    // see screenshot
    //
    shortestCycle.push(shortestEdge);
}

function info(what) {
    push();
    noStroke();
    text(what, 800, infoLine);
    infoLine += INFO_LINE_HEIGHT;
    pop();
}

function drawComplete() {
    strokeWeight(1);
    stroke(0, 0, 0, 100);
    fill(0);
    
    for (var i = 0; i < V.length; i++) {
        text(V[i].name, V[i].x - 20, V[i].y - 5);
        ellipse(V[i].x, V[i].y, 3);
    } 
    
    info("edges:");
    for (var j = 0; j < E.length; j++) {
        line(E[j].v1.x, E[j].v1.y, E[j].v2.x, E[j].v2.y);
        info("edge "+E[j].v1.name+" to "+E[j].v2.name+" d="+round(E[j].d));
    }
}
