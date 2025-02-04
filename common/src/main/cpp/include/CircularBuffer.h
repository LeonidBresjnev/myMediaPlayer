#pragma once


#include <cstddef>

namespace equalizer {
    class CircularBuffer {

    public:
        float buffer[3]{0.f, 0.f, 0.f};
        size_t head;
        size_t tail;
        size_t size;

        explicit CircularBuffer(size_t size_) {
            head = 0;
            size = size_;
            tail = 0;
        }

        void push_back(float item) {
            buffer[tail] = item;
            head = (head + 1) % size;
            tail = (tail + 1) % size;
        }

    };
}
