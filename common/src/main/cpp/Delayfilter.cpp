#include "FilterSource.h"

#include <iostream>


namespace equalizer {
    Delayfilter::Delayfilter() {
        head = 0;
        size = 0;
    }

    void Delayfilter::setSize(size_t newSize) {
        size = newSize;
        for (auto i = 0; i < size; i++) {
            buffer[i] = 0;
        }
    }

    float Delayfilter::getSample() {
        return buffer[head];
    }

    void Delayfilter::setSample(float sample) {
        push_back(sample);
    }

    void Delayfilter::onPlaybackStopped() {
    }

    void Delayfilter::push_back(float item) {
        buffer[head] = item;
        head = (head + 1) % size;
        // std::cout << "Pushed back: " << " " <<head<< std::endl;
    }
}