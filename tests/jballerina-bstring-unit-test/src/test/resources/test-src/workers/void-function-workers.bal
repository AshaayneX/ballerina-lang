import ballerina/runtime;

int i = 0;

function testVoidFunction() returns int {
    testVoid();
    runtime:sleep(1500);
    return i;
}

function testVoid() {
    @strand{thread:"any"}
    worker w1 {
        runtime:sleep(3000);
        testNew();
    }
    @strand{thread:"any"}
    worker w2 {
         int x = i + 10;
         i = 10;
    }
}

function testNew(){
    @strand{thread:"any"}
    worker w1 {
        runtime:sleep(2000);
    }
    @strand{thread:"any"}
    worker w2 {
        i = 5;
    }
}

