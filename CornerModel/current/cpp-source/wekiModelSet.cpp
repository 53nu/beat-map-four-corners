#include <vector>
#include "wekiModelSet.h"

wekiModelSet::wekiModelSet() {
    std::vector<baseModel*> myModelSet;
};

wekiModelSet::~wekiModelSet() {
    for (std::vector<baseModel*>::iterator i = myModelSet.begin(); i != myModelSet.end(); ++i) {
        delete *i;
    }
};

void wekiModelSet::initModelSet() {
   std::vector<int> whichInputs;
}

double* wekiModelSet::passInputToModels(double* input) {
    int setSize = myModelSet.size();
    double* output = new double[setSize];
    for (int i = 0; i < setSize; ++i) {
        output[i] = myModelSet[i]->processInput(input);
    }
    return output;
}