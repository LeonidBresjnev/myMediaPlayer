#include "stream.h"
#include <string>

void  stream::output_silence (mp3_sample * buffer, int n)
{                                                                    /*  74 */

    for (int i = 0; i < n; i++) {
        double *v;                                                       /*  75 */

        {
            int ch = 0;

            this->offset[ch] = this->offset[ch] - SUBBANDS;                      /*   4 */
            if (this->offset[ch] < 0) {
                this->offset[ch] = SHIFTSIZE - WINDOWBLOCKS * SUBBANDS;
                memmove (&(this->w[ch][this->offset[ch] + SUBBANDS]),
                         &(this->w[ch][0]),
                         sizeof (double) * (WINDOWBLOCKS - 1) * SUBBANDS);
            }
            v = this->w[ch] + this->offset[ch];
            for (int sb = SUBBANDS - 1; sb >= 0; sb--)
                v[sb] = 0.0;
            windowing (v, buffer);
        }
        if (this->info.channels > 1) {
            int ch = 1;

            this->offset[ch] = this->offset[ch] - SUBBANDS;                      /*   4 */
            if (this->offset[ch] < 0) {
                this->offset[ch] = SHIFTSIZE - WINDOWBLOCKS * SUBBANDS;
                memmove (&(this->w[ch][this->offset[ch] + SUBBANDS]),
                         &(this->w[ch][0]),
                         sizeof (double) * (WINDOWBLOCKS - 1) * SUBBANDS);
            }
            v = this->w[ch] + this->offset[ch];
            for (int sb = SUBBANDS - 1; sb >= 0; sb--)
                v[sb] = 0.0;
            windowing (v, buffer + 1);
            buffer = buffer + 2 * SUBBANDS;
        }
        else {                                                  /*  71 */

            if (this->options.flags & MP3_TWO_CHANNEL_MONO) {
                for (int sb = 0; sb < SUBBANDS; sb++)
                    buffer[2 * sb + 1] = buffer[2 * sb];
                buffer = buffer + 2 * SUBBANDS;
            }
            else {
                for (int sb = 0; sb < SUBBANDS; sb++)
                    buffer[sb] = buffer[2 * sb];
                buffer = buffer + SUBBANDS;
            }
        }
    }
    if (this->options.flags & MP3_TWO_CHANNEL_MONO)
        this->info.samples += n * 2 * SUBBANDS;
    else
        this->info.samples += n * this->info.channels * SUBBANDS;
}



void    stream::output_repeat ( mp3_sample * buffer, int n, int d)
{
    for (int i = 0; i < n; i++) {
        int ch;                                                          /* 408 */

        double *v;

        {
            ch = 0;
            this->offset[ch] = this->offset[ch] - SUBBANDS;                      /*   4 */
            if (this->offset[ch] < 0) {
                this->offset[ch] = SHIFTSIZE - WINDOWBLOCKS * SUBBANDS;
                memmove (&(this->w[ch][this->offset[ch] + SUBBANDS]),
                         &(this->w[ch][0]),
                         sizeof (double) * (WINDOWBLOCKS - 1) * SUBBANDS);
            }
            v = this->w[ch] + this->offset[ch];
            {
                int previous_offset;                                         /* 407 */

                previous_offset = this->offset[ch] + d * SUBBANDS;
                if (previous_offset >= SHIFTSIZE)
                    previous_offset =
                            previous_offset - SHIFTSIZE + (WINDOWBLOCKS - 1) * SUBBANDS;
                memmove (v, this->w[ch] + previous_offset, sizeof (double) * SUBBANDS);
            }
            windowing (v, buffer);
        }
        if (this->info.channels > 1) {
            ch = 1;
            this->offset[ch] = this->offset[ch] - SUBBANDS;                      /*   4 */
            if (this->offset[ch] < 0) {
                this->offset[ch] = SHIFTSIZE - WINDOWBLOCKS * SUBBANDS;
                memmove (&(this->w[ch][this->offset[ch] + SUBBANDS]),
                         &(this->w[ch][0]),
                         sizeof (double) * (WINDOWBLOCKS - 1) * SUBBANDS);
            }
            v = this->w[ch] + this->offset[ch];
            {
                int previous_offset = this->offset[ch] + d * SUBBANDS;
                if (previous_offset >= SHIFTSIZE)
                    previous_offset =
                            previous_offset - SHIFTSIZE + (WINDOWBLOCKS - 1) * SUBBANDS;
                memmove (v, this->w[ch] + previous_offset, sizeof (double) * SUBBANDS);
            }
            windowing (v, buffer + 1);
            buffer = buffer + 2 * SUBBANDS;
        }
        else {                                                    /*  71 */

            if (this->options.flags & MP3_TWO_CHANNEL_MONO) {
                for (int sb = 0; sb < SUBBANDS; sb++)
                    buffer[2 * sb + 1] = buffer[2 * sb];
                buffer = buffer + 2 * SUBBANDS;
            }
            else {
                for (int sb = 0; sb < SUBBANDS; sb++)
                    buffer[sb] = buffer[2 * sb];
                buffer = buffer + SUBBANDS;
            }
        }
    }
    if (this->options.flags & MP3_TWO_CHANNEL_MONO)
        this->info.samples += n * 2 * SUBBANDS;
    else
        this->info.samples += n * this->info.channels * SUBBANDS;
}

short int *
stream::decode_small_A ( short int *u, int bits_available, int n) const
{                                                                    /* 302 */
    unsigned char *byte_pointer = this->byte_pointer;                     /* 313 */

    int huffman_cache_size = 8 - this->bit_offset;

    int huffman_cache =
            (*byte_pointer++) << (sizeof (int) * 8 - huffman_cache_size);
    bits_available = bits_available - huffman_cache_size;
    while (n >= 4) {

        if ((16 - huffman_cache_size > 0)                                /* 315 */
                ) {
            {
                int tmp = (*byte_pointer << 8) | (*(byte_pointer + 1) & 0xFF);  /* 316 */

                byte_pointer = byte_pointer + 2;
                huffman_cache = huffman_cache | (tmp << (16 - huffman_cache_size));
                huffman_cache_size = huffman_cache_size + 16;
            }
            bits_available = bits_available - 16;                          /* 318 */
            if (bits_available < 0) {
                huffman_cache_size = huffman_cache_size + bits_available;
                bits_available = 0;
                if (huffman_cache_size <= 0)
                    break;
            }
        }
        unsigned char code = htabA[((unsigned int) huffman_cache) >> (HUFFMAN_CACHE_SIZE - 6)];   /* 299 */
        huffman_cache = huffman_cache << (code & 0xF);
        huffman_cache_size = huffman_cache_size - (code & 0xF);
        code = (code & 0xF0) | (((unsigned int) huffman_cache) >> (HUFFMAN_CACHE_SIZE - 4));        /* 300 */
        *u++ = signed_small_values[code].u0;
        *u++ = signed_small_values[code].u1;
        *u++ = signed_small_values[code].u2;
        *u++ = signed_small_values[code].u3;
        huffman_cache = huffman_cache << (signed_small_values[code].n);
        huffman_cache_size = huffman_cache_size - (signed_small_values[code].n);
        n = n - 4;
    }
    return u;
}


short int *
stream::decode_small_B ( short int *u, int bits_available, int n) const
{                                                                    /* 303 */
    unsigned char *byte_pointer = this->byte_pointer;                     /* 313 */

    int huffman_cache_size = 8 - this->bit_offset;

    int huffman_cache =
            (*byte_pointer++) << (sizeof (int) * 8 - huffman_cache_size);
    bits_available = bits_available - huffman_cache_size;
    while (n >= 4) {
        unsigned char code;

        if ((16 - huffman_cache_size > 0)                                /* 315 */
                ) {
            {
                int tmp = (*byte_pointer << 8) | (*(byte_pointer + 1) & 0xFF);  /* 316 */

                byte_pointer = byte_pointer + 2;
                huffman_cache = huffman_cache | (tmp << (16 - huffman_cache_size));
                huffman_cache_size = huffman_cache_size + 16;
            }
            bits_available = bits_available - 16;                          /* 318 */
            if (bits_available < 0) {
                huffman_cache_size = huffman_cache_size + bits_available;
                bits_available = 0;
                if (huffman_cache_size <= 0)
                    break;
            }
        }
        code = ~(huffman_cache >> (HUFFMAN_CACHE_SIZE - 4));             /* 301 */
        huffman_cache = huffman_cache << 4;
        huffman_cache_size = huffman_cache_size - 4;
        code = code << 4;
        code = (code & 0xF0) | (((unsigned int) huffman_cache) >> (HUFFMAN_CACHE_SIZE - 4));        /* 300 */
        *u++ = signed_small_values[code].u0;
        *u++ = signed_small_values[code].u1;
        *u++ = signed_small_values[code].u2;
        *u++ = signed_small_values[code].u3;
        huffman_cache = huffman_cache << (signed_small_values[code].n);
        huffman_cache_size = huffman_cache_size - (signed_small_values[code].n);
        n = n - 4;
    }
    return u;
}

void
stream::decode_big ( short int *u, int k, int n)
{                                                                    /* 310 */
    short int *htab = huffman_tables[k].h;

    unsigned char *byte_pointer = this->byte_pointer;                     /* 313 */

    int huffman_cache_size = 8 - this->bit_offset;

    int huffman_cache =
            (*byte_pointer++) << (sizeof (int) * 8 - huffman_cache_size);
    while (n-- > 0) {
        int code;

        if ((16 - huffman_cache_size > 0)                                /* 315 */
                ) {
            int tmp = (*byte_pointer << 8) | (*(byte_pointer + 1) & 0xFF); /* 316 */

            byte_pointer = byte_pointer + 2;
            huffman_cache = huffman_cache | (tmp << (16 - huffman_cache_size));
            huffman_cache_size = huffman_cache_size + 16;
        }
        {
            int width = HWIDTH;                                            /* 305 */

            short int *h = htab;

            code = h[((unsigned int) huffman_cache) >> (HUFFMAN_CACHE_SIZE - width)];
            while (code < 0) {
                huffman_cache = huffman_cache << width;
                huffman_cache_size = huffman_cache_size - width;
                h = h - (code >> 4);
                width = code & 0x0F;
                code =
                        h[((unsigned int) huffman_cache) >> (HUFFMAN_CACHE_SIZE - width)];
            }
        }
        huffman_cache = huffman_cache << (code >> 8);                    /* 306 */
        huffman_cache_size = huffman_cache_size - (code >> 8);
        code = code & 0xFF;
        {
            short int value = code >> 4;                                   /* 307 */

            if (value != 0) {
                int tmp = ((signed int) huffman_cache) >> (HUFFMAN_CACHE_SIZE - 1);     /* 308 */

                value = (value ^ tmp) - tmp;
                huffman_cache = huffman_cache << 1;
                huffman_cache_size--;
            }
            *u++ = value;
        }
        code = (code << 4) & 0xFF;
        {
            short int value = code >> 4;                                   /* 307 */

            if (value != 0) {
                int tmp = ((signed int) huffman_cache) >> (HUFFMAN_CACHE_SIZE - 1);     /* 308 */

                value = (value ^ tmp) - tmp;
                huffman_cache = huffman_cache << 1;
                huffman_cache_size--;
            }
            *u++ = value;
        }
     //   code = (code << 4) & 0xFF;
    }
    this->bit_offset = (8 - (huffman_cache_size % 8)) & 0x7;              /* 314 */
    this->byte_pointer = byte_pointer - ((huffman_cache_size + this->bit_offset) / 8);
}

void   stream::decode_very_big ( short int *u, int k, int n)
{                                                                    /* 311 */
    short int *htab = huffman_tables[k].h;

    int linbits = huffman_tables[k].linbits;

    unsigned char *byte_pointer = this->byte_pointer;                     /* 313 */

    int huffman_cache_size = 8 - this->bit_offset;

    int huffman_cache =
            (*byte_pointer++) << (sizeof (int) * 8 - huffman_cache_size);
    while (n-- > 0) {
        int code;

        if ((16 - huffman_cache_size > 0)                                /* 315 */
                ) {
            int tmp = (*byte_pointer << 8) | (*(byte_pointer + 1) & 0xFF); /* 316 */

            byte_pointer = byte_pointer + 2;
            huffman_cache = huffman_cache | (tmp << (16 - huffman_cache_size));
            huffman_cache_size = huffman_cache_size + 16;
        }
        if (huffman_cache_size < 24) {                                   /* 317 */
            huffman_cache =
                    huffman_cache | (byte_pointer[0] << (24 - huffman_cache_size));
            byte_pointer++;
            huffman_cache_size = huffman_cache_size + 8;
        }
        {
            int width = HWIDTH;                                            /* 305 */

            short int *h = htab;

            code = h[((unsigned int) huffman_cache) >> (HUFFMAN_CACHE_SIZE - width)];
            while (code < 0) {
                huffman_cache = huffman_cache << width;
                huffman_cache_size = huffman_cache_size - width;
                h = h - (code >> 4);
                width = code & 0x0F;
                code =
                        h[((unsigned int) huffman_cache) >> (HUFFMAN_CACHE_SIZE - width)];
            }
        }
        huffman_cache = huffman_cache << (code >> 8);                    /* 306 */
        huffman_cache_size = huffman_cache_size - (code >> 8);
        code = code & 0xFF;
        if ((16 - huffman_cache_size > 0)                                /* 315 */
                ) {
            int tmp = (*byte_pointer << 8) | (*(byte_pointer + 1) & 0xFF); /* 316 */

            byte_pointer = byte_pointer + 2;
            huffman_cache = huffman_cache | (tmp << (16 - huffman_cache_size));
            huffman_cache_size = huffman_cache_size + 16;
        }
        {
            short int value = code >> 4;                                   /* 309 */

            if (value == 15 && linbits > 0) {
                value =
                        value +
                        (((unsigned int) huffman_cache) >> (HUFFMAN_CACHE_SIZE - linbits));
                huffman_cache = huffman_cache << linbits;
                huffman_cache_size = huffman_cache_size - linbits;
                {
                    int tmp = ((signed int) huffman_cache) >> (HUFFMAN_CACHE_SIZE - 1);   /* 308 */

                    value = (value ^ tmp) - tmp;
                    huffman_cache = huffman_cache << 1;
                    huffman_cache_size--;
                }
            }
            else if (value != 0) {
                int tmp = ((signed int) huffman_cache) >> (HUFFMAN_CACHE_SIZE - 1);     /* 308 */

                value = (value ^ tmp) - tmp;
                huffman_cache = huffman_cache << 1;
                huffman_cache_size--;
            }
            *u++ = value;
            code = (code << 4) & 0xFF;
        }
        if ((16 - huffman_cache_size > 0)                                /* 315 */
                ) {
            int tmp = (*byte_pointer << 8) | (*(byte_pointer + 1) & 0xFF); /* 316 */

            byte_pointer = byte_pointer + 2;
            huffman_cache = huffman_cache | (tmp << (16 - huffman_cache_size));
            huffman_cache_size = huffman_cache_size + 16;
        }
        {
            short int value = code >> 4;                                   /* 309 */

            if (value == 15 && linbits > 0) {
                value =
                        value +
                        (((unsigned int) huffman_cache) >> (HUFFMAN_CACHE_SIZE - linbits));
                huffman_cache = huffman_cache << linbits;
                huffman_cache_size = huffman_cache_size - linbits;
                {
                    int tmp = ((signed int) huffman_cache) >> (HUFFMAN_CACHE_SIZE - 1);   /* 308 */

                    value = (value ^ tmp) - tmp;
                    huffman_cache = huffman_cache << 1;
                    huffman_cache_size--;
                }
            }
            else if (value != 0) {
                int tmp = ((signed int) huffman_cache) >> (HUFFMAN_CACHE_SIZE - 1);     /* 308 */

                value = (value ^ tmp) - tmp;
                huffman_cache = huffman_cache << 1;
                huffman_cache_size--;
            }
            *u++ = value;
         //   code = (code << 4) & 0xFF;
        }
    }
    this->bit_offset = (8 - (huffman_cache_size % 8)) & 0x7;              /* 314 */
    this->byte_pointer = byte_pointer - ((huffman_cache_size + this->bit_offset) / 8);
}

unsigned int stream::getbit (const int n)
{                                                                    /* 144 */


    char bit_offset = this->bit_offset;                          /* 146 */

    unsigned char *byte_pointer = this->byte_pointer;

    unsigned int bits = ((unsigned int) byte_pointer[0]) << (sizeof (bits) * 8 - 8);   /* 142 */
    bits |= ((unsigned int) byte_pointer[1]) << (sizeof (bits) * 8 - 16);
    bits |= ((unsigned int) byte_pointer[2]) << (sizeof (bits) * 8 - 24);
    bits <<= bit_offset;                                               /* 143 */
    bit_offset += n;
    byte_pointer += (bit_offset >> 3);
    bit_offset = bit_offset & 0x07;
    bits = (bits >> (sizeof (bits) * 8 - n));
    this->bit_offset = bit_offset;                                        /* 147 */
    this->byte_pointer = byte_pointer;
    return bits;
}

void stream::layer_I_decode_samples ()
{                                                                    /* 122 */
    char bit_offset = this->bit_offset;                          /* 146 */

    unsigned char *byte_pointer = this->byte_pointer;

    {                                                          /* 116 */

        if (this->info.channels > 1)
            for (int i = 0; i < 12; i++) {
                int sb;                                                      /* 120 */

                for (sb = 0; sb < this->info.bound; sb++) {
                    int sample;                                                /* 118 */

                    {
                        int n = side_info[sb][0].bit_allocation;

                        if (n == 0)                                              /* 121 */
                            sample = 0;
                        else {
                            unsigned int bits = ((unsigned int) byte_pointer[0]) << (sizeof (bits) * 8 - 8);       /* 142 */
                            bits |=
                                    ((unsigned int) byte_pointer[1]) << (sizeof (bits) * 8 - 16);
                            bits |=
                                    ((unsigned int) byte_pointer[2]) << (sizeof (bits) * 8 - 24);
                            bits <<= bit_offset;                                   /* 143 */
                            bit_offset += n;
                            byte_pointer += (bit_offset >> 3);
                            bit_offset = bit_offset & 0x07;
                            bits = bits ^ (((unsigned int) -1) >> 1)               /* 123 */
                                    ;
                            bits = bits & ~(((unsigned int) -1) >> n)              /* 124 */
                                    ;
                            sample = (int) bits;
                        }
                        y[i][0][sb] = sample * side_info[sb][0].mfactor[0];
                    }
                    {
                        int n = side_info[sb][1].bit_allocation;                 /* 119 */

                        if (n == 0)                                              /* 121 */
                            sample = 0;
                        else {
                            unsigned int bits = ((unsigned int) byte_pointer[0]) << (sizeof (bits) * 8 - 8);       /* 142 */
                            bits |=
                                    ((unsigned int) byte_pointer[1]) << (sizeof (bits) * 8 - 16);
                            bits |=
                                    ((unsigned int) byte_pointer[2]) << (sizeof (bits) * 8 - 24);
                            bits <<= bit_offset;                                   /* 143 */
                            bit_offset += n;
                            byte_pointer += (bit_offset >> 3);
                            bit_offset = bit_offset & 0x07;
                            bits = bits ^ (((unsigned int) -1) >> 1)               /* 123 */
                                    ;
                            bits = bits & ~(((unsigned int) -1) >> n)              /* 124 */
                                    ;
                            sample = (int) bits;
                        }
                        y[i][1][sb] = sample * side_info[sb][1].mfactor[0];
                    }
                }
                for (; sb < SUBBANDS; sb++) {
                    int sample;                                                /* 118 */

                    {
                        int n = side_info[sb][0].bit_allocation;

                        if (n == 0)                                              /* 121 */
                            sample = 0;
                        else {
                            unsigned int bits = ((unsigned int) byte_pointer[0]) << (sizeof (bits) * 8 - 8);       /* 142 */
                            bits |=
                                    ((unsigned int) byte_pointer[1]) << (sizeof (bits) * 8 - 16);
                            bits |=
                                    ((unsigned int) byte_pointer[2]) << (sizeof (bits) * 8 - 24);
                            bits <<= bit_offset;                                   /* 143 */
                            bit_offset += n;
                            byte_pointer += (bit_offset >> 3);
                            bit_offset = bit_offset & 0x07;
                            bits = bits ^ (((unsigned int) -1) >> 1)               /* 123 */
                                    ;
                            bits = bits & ~(((unsigned int) -1) >> n)              /* 124 */
                                    ;
                            sample = (int) bits;
                        }
                        y[i][0][sb] = sample * side_info[sb][0].mfactor[0];
                    }
                    y[i][1][sb] = sample * side_info[sb][1].mfactor[0];
                }
            }
        else
            for (int i = 0; i < 12; i++) {                                /* 117 */

                for (int sb = 0; sb < SUBBANDS; sb++) {
                    int sample;                                                /* 118 */

                    {
                        int n = side_info[sb][0].bit_allocation;

                        if (n == 0)                                              /* 121 */
                            sample = 0;
                        else {
                            unsigned int bits = ((unsigned int) byte_pointer[0]) << (sizeof (bits) * 8 - 8);       /* 142 */
                            bits |=
                                    ((unsigned int) byte_pointer[1]) << (sizeof (bits) * 8 - 16);
                            bits |=
                                    ((unsigned int) byte_pointer[2]) << (sizeof (bits) * 8 - 24);
                            bits <<= bit_offset;                                   /* 143 */
                            bit_offset += n;
                            byte_pointer += (bit_offset >> 3);
                            bit_offset = bit_offset & 0x07;
                            bits = bits ^ (((unsigned int) -1) >> 1)               /* 123 */
                                    ;
                            bits = bits & ~(((unsigned int) -1) >> n)              /* 124 */
                                    ;
                            sample = (int) bits;
                        }
                        y[i][0][sb] = sample * side_info[sb][0].mfactor[0];
                    }
                }
            }
    }
    this->bit_offset = bit_offset;                                        /* 147 */
    this->byte_pointer = byte_pointer;
}

short unsigned int  stream::crc_check ()
{                                                                    /* 101 */
    short unsigned int crc;

    unsigned char *byte_pointer;

    int n;

    crc = 0xFFFF;
    byte_pointer = this->frame + 2;                                       /* 100 */
    crc = (crc << 8) ^ crc_table[(crc >> 8) ^ *byte_pointer++]         /* 441 */
            ;
    crc = (crc << 8) ^ crc_table[(crc >> 8) ^ *byte_pointer++]         /* 441 */
            ;
    byte_pointer++;
    byte_pointer++;
    if (this->info.layer == 1) {
        n = 16;
        if (this->info.channels > 1)
            n = n + this->info.bound / 2;
        while (n-- > 0)
            crc = (crc << 8) ^ crc_table[(crc >> 8) ^ *byte_pointer++]     /* 441 */
                    ;
    }
    else if (this->info.layer == 2) {
        char bit_offset = 0;                                             /* 215 */

        unsigned int bits;

        int sb, ch, nsf;

        nsf = 0;                                                         /* 213 */
        for (sb = 0; sb < this->info.bound; sb++)
            for (ch = 0; ch < this->info.channels; ch++) {
                n = this->nbal[sb];
                if (n > 0) {
                    bits = ((unsigned int) byte_pointer[0]) << (sizeof (bits) * 8 - 8);   /* 142 */
                    bits |= ((unsigned int) byte_pointer[1]) << (sizeof (bits) * 8 - 16);
                    bits |= ((unsigned int) byte_pointer[2]) << (sizeof (bits) * 8 - 24);
                    bits <<= bit_offset;                                       /* 143 */
                    bit_offset += n;
                    byte_pointer += (bit_offset >> 3);
                    bit_offset = bit_offset & 0x07;
                    bits = (bits >> (sizeof (bits) * 8 - n));
                    crc = bitcrc (crc, (unsigned short int) bits, n);
                    if (bits != 0)
                        nsf++;
                }
            }
        for (; sb < this->sblimit[0]; sb++) {
            n = this->nbal[sb];
            if (n > 0) {
                bits = ((unsigned int) byte_pointer[0]) << (sizeof (bits) * 8 - 8);     /* 142 */
                bits |= ((unsigned int) byte_pointer[1]) << (sizeof (bits) * 8 - 16);
                bits |= ((unsigned int) byte_pointer[2]) << (sizeof (bits) * 8 - 24);
                bits <<= bit_offset;                                         /* 143 */
                bit_offset += n;
                byte_pointer += (bit_offset >> 3);
                bit_offset = bit_offset & 0x07;
                bits = (bits >> (sizeof (bits) * 8 - n));
                crc = bitcrc (crc, (unsigned short int) bits, n);
                if (bits != 0)
                    nsf = nsf + this->info.channels;
            }
        }
        {
            int sfbit = nsf * 2;                                           /* 214 */

            if (bit_offset > 0) {
                n = min (8 - bit_offset, sfbit);
                bits = ((unsigned int) byte_pointer[0]) << (sizeof (bits) * 8 - 8);     /* 142 */
                bits |= ((unsigned int) byte_pointer[1]) << (sizeof (bits) * 8 - 16);
                bits |= ((unsigned int) byte_pointer[2]) << (sizeof (bits) * 8 - 24);
                bits <<= bit_offset;                                         /* 143 */
                bit_offset += n;
                byte_pointer += (bit_offset >> 3);
                bit_offset = bit_offset & 0x07;
                bits = (bits >> (sizeof (bits) * 8 - n));
                crc = bitcrc (crc, (unsigned short int) bits, n);
                sfbit = sfbit - n;
            }
            while (sfbit >= 8) {
                crc = (crc << 8) ^ crc_table[(crc >> 8) ^ *byte_pointer++]   /* 441 */
                        ;
                sfbit = sfbit - 8;
            }
            if (sfbit > 0) {
                n = sfbit;
                bits = ((unsigned int) byte_pointer[0]) << (sizeof (bits) * 8 - 8);     /* 142 */
                bits |= ((unsigned int) byte_pointer[1]) << (sizeof (bits) * 8 - 16);
                bits |= ((unsigned int) byte_pointer[2]) << (sizeof (bits) * 8 - 24);
                bits <<= bit_offset;                                         /* 143 */
             //   bit_offset += n;
             //   byte_pointer += (bit_offset >> 3);
            //    bit_offset = bit_offset & 0x07;
                bits = (bits >> (sizeof (bits) * 8 - n));
                crc = bitcrc (crc, (unsigned short int) bits, n);
            }
        }
    }
    else {
        n = this->info.fixed_size - HEADER_SIZE - 2;
        while (n-- > 0)
            crc = (crc << 8) ^ crc_table[(crc >> 8) ^ *byte_pointer++]     /* 441 */
                    ;
    }
    return crc;
}

void
stream::layer_II_decode_samples ( int g)
{                                                                    /* 217 */
    char bit_offset = this->bit_offset;                          /* 146 */

    unsigned char *byte_pointer = this->byte_pointer;

    {                                                     /* 218 */

        for (int i = 0; i < 12; i = i + 3) {

            for (int sb = 0; sb < this->info.bound; sb++)
                for (int ch = 0; ch < this->info.channels; ch++) {
                    double f = side_info[sb][ch].mfactor[g];

                    int n = side_info[sb][ch].bit_allocation;

                    {
                        int sample;                                              /* 201 */

                        if (n > 0) {
                            {
                                unsigned int bits;                                   /* 125 */

                                bits = ((unsigned int) byte_pointer[0]) << (sizeof (bits) * 8 - 8);     /* 142 */
                                bits |=
                                        ((unsigned int) byte_pointer[1]) << (sizeof (bits) * 8 - 16);
                                bits |=
                                        ((unsigned int) byte_pointer[2]) << (sizeof (bits) * 8 - 24);
                                bits <<= bit_offset;                                 /* 143 */
                                bit_offset += n;
                                byte_pointer += (bit_offset >> 3);
                                bit_offset = bit_offset & 0x07;
                                bits = bits ^ (((unsigned int) -1) >> 1)             /* 123 */
                                        ;
                                bits = bits & ~(((unsigned int) -1) >> n)            /* 124 */
                                        ;
                                sample = (int) bits;
                            }
                            y[i][ch][sb] = sample * f;
                            {
                                unsigned int bits;                                   /* 125 */

                                bits = ((unsigned int) byte_pointer[0]) << (sizeof (bits) * 8 - 8);     /* 142 */
                                bits |=
                                        ((unsigned int) byte_pointer[1]) << (sizeof (bits) * 8 - 16);
                                bits |=
                                        ((unsigned int) byte_pointer[2]) << (sizeof (bits) * 8 - 24);
                                bits <<= bit_offset;                                 /* 143 */
                                bit_offset += n;
                                byte_pointer += (bit_offset >> 3);
                                bit_offset = bit_offset & 0x07;
                                bits = bits ^ (((unsigned int) -1) >> 1)             /* 123 */
                                        ;
                                bits = bits & ~(((unsigned int) -1) >> n)            /* 124 */
                                        ;
                                sample = (int) bits;
                            }
                            y[i + 1][ch][sb] = sample * f;
                            {
                                unsigned int bits;                                   /* 125 */

                                bits = ((unsigned int) byte_pointer[0]) << (sizeof (bits) * 8 - 8);     /* 142 */
                                bits |=
                                        ((unsigned int) byte_pointer[1]) << (sizeof (bits) * 8 - 16);
                                bits |=
                                        ((unsigned int) byte_pointer[2]) << (sizeof (bits) * 8 - 24);
                                bits <<= bit_offset;                                 /* 143 */
                                bit_offset += n;
                                byte_pointer += (bit_offset >> 3);
                                bit_offset = bit_offset & 0x07;
                                bits = bits ^ (((unsigned int) -1) >> 1)             /* 123 */
                                        ;
                                bits = bits & ~(((unsigned int) -1) >> n)            /* 124 */
                                        ;
                                sample = (int) bits;
                            }
                            y[i + 2][ch][sb] = sample * f;
                        }
                        else if (n < 0) {                                        /* 424 */
                            int c;

                            {
                                unsigned int bits;

                                n = -n;
                                bits = ((unsigned int) byte_pointer[0]) << (sizeof (bits) * 8 - 8);     /* 142 */
                                bits |=
                                        ((unsigned int) byte_pointer[1]) << (sizeof (bits) * 8 - 16);
                                bits |=
                                        ((unsigned int) byte_pointer[2]) << (sizeof (bits) * 8 - 24);
                                bits <<= bit_offset;                                 /* 143 */
                                bit_offset += n;
                                byte_pointer += (bit_offset >> 3);
                                bit_offset = bit_offset & 0x07;
                                bits = (bits >> (sizeof (bits) * 8 - n));
                                n = -n;
                                c = bits;
                            }
                            {
                                const int (*table)[3];                               /* 425 */

                                table = degroup[-n - 5];
                                y[i][ch][sb] = table[c][0] * f;                      /* 426 */
                                y[i + 1][ch][sb] = table[c][1] * f;
                                y[i + 2][ch][sb] = table[c][2] * f;
                            }}
                        else
                            y[i][ch][sb] = y[i + 1][ch][sb] = y[i + 2][ch][sb] = 0.0;
                    }
                }
            for (int sb = this->info.bound; sb < this->sblimit[0]; sb++) {
                const double f = side_info[sb][0].mfactor[g];

                const double r = side_info[sb][1].mfactor[g] / f;

                int n = side_info[sb][0].bit_allocation;

                int ch = 0;
                {
                    int sample;                                                /* 201 */

                    if (n > 0) {
                        {
                            unsigned int bits;                                     /* 125 */

                            bits = ((unsigned int) byte_pointer[0]) << (sizeof (bits) * 8 - 8);       /* 142 */
                            bits |=
                                    ((unsigned int) byte_pointer[1]) << (sizeof (bits) * 8 - 16);
                            bits |=
                                    ((unsigned int) byte_pointer[2]) << (sizeof (bits) * 8 - 24);
                            bits <<= bit_offset;                                   /* 143 */
                            bit_offset += n;
                            byte_pointer += (bit_offset >> 3);
                            bit_offset = bit_offset & 0x07;
                            bits = bits ^ (((unsigned int) -1) >> 1)               /* 123 */
                                    ;
                            bits = bits & ~(((unsigned int) -1) >> n)              /* 124 */
                                    ;
                            sample = (int) bits;
                        }
                        y[i][ch][sb] = sample * f;
                        {
                            unsigned int bits = ((unsigned int) byte_pointer[0]) << (sizeof (bits) * 8 - 8);       /* 142 */
                            bits |=
                                    ((unsigned int) byte_pointer[1]) << (sizeof (bits) * 8 - 16);
                            bits |=
                                    ((unsigned int) byte_pointer[2]) << (sizeof (bits) * 8 - 24);
                            bits <<= bit_offset;                                   /* 143 */
                            bit_offset += n;
                            byte_pointer += (bit_offset >> 3);
                            bit_offset = bit_offset & 0x07;
                            bits = bits ^ (((unsigned int) -1) >> 1)               /* 123 */
                                    ;
                            bits = bits & ~(((unsigned int) -1) >> n)              /* 124 */
                                    ;
                            sample = (int) bits;
                        }
                        y[i + 1][ch][sb] = sample * f;
                        {
                            unsigned int bits = ((unsigned int) byte_pointer[0]) << (sizeof (bits) * 8 - 8);       /* 142 */
                            bits |=
                                    ((unsigned int) byte_pointer[1]) << (sizeof (bits) * 8 - 16);
                            bits |=
                                    ((unsigned int) byte_pointer[2]) << (sizeof (bits) * 8 - 24);
                            bits <<= bit_offset;                                   /* 143 */
                            bit_offset += n;
                            byte_pointer += (bit_offset >> 3);
                            bit_offset = bit_offset & 0x07;
                            bits = bits ^ (((unsigned int) -1) >> 1)               /* 123 */
                                    ;
                            bits = bits & ~(((unsigned int) -1) >> n)              /* 124 */
                                    ;
                            sample = (int) bits;
                        }
                        y[i + 2][ch][sb] = sample * f;
                    }
                    else if (n < 0) {                                          /* 424 */
                        unsigned int c;

                        {
                            n = -n;
                            unsigned int bits = ((unsigned int) byte_pointer[0]) << (sizeof (bits) * 8 - 8);       /* 142 */
                            bits |=
                                    ((unsigned int) byte_pointer[1]) << (sizeof (bits) * 8 - 16);
                            bits |=
                                    ((unsigned int) byte_pointer[2]) << (sizeof (bits) * 8 - 24);
                            bits <<= bit_offset;                                   /* 143 */
                            bit_offset += n;
                            byte_pointer += (bit_offset >> 3);
                            bit_offset = bit_offset & 0x07;
                            bits = (bits >> (sizeof (bits) * 8 - n));
                            n = -n;
                            c = bits;
                        }
                        {
                            const int (*table)[3];                                 /* 425 */

                            table = degroup[-n - 5];
                            y[i][ch][sb] = table[c][0] * f;                        /* 426 */
                            y[i + 1][ch][sb] = table[c][1] * f;
                            y[i + 2][ch][sb] = table[c][2] * f;
                        }}
                    else
                        y[i][ch][sb] = y[i + 1][ch][sb] = y[i + 2][ch][sb] = 0.0;
                }
                if (this->info.channels > 1) {
                    y[i][1][sb] = y[i][0][sb] * r;
                    y[i + 1][1][sb] = y[i + 1][0][sb] * r;
                    y[i + 2][1][sb] = y[i + 2][0][sb] * r;
                }
            }
            for (int sb = this->sblimit[0]; sb < SUBBANDS; sb++) {
                y[i][0][sb] = y[i + 1][0][sb]
                        = y[i + 2][0][sb] = 0.0;
                if (this->info.channels > 1)
                    y[i][1][sb] = y[i + 1][1][sb]
                            = y[i + 2][1][sb] = 0.0;
            }
        }
    }
    this->bit_offset = bit_offset;                                        /* 147 */
    this->byte_pointer = byte_pointer;
}


unsigned char *
stream::next_frame ()
{                                                                    /* 166 */
    int previous_free_format;

    this->frame = this->frame + this->info.frame_size;
    this->info.frame++;
    while (this->frame + HEADER_SIZE > this->finish)
        if (this->state & END_OF_INPUT)
            return nullptr;
        else
            fill_input_buffer ();
    previous_free_format = this->info.free_format;
    if (!(&this->info)->decode_header ( this->frame))
        return nullptr;
    this->byte_pointer = this->frame;
    this->bit_offset = 0;
    if (this->info.free_format && !previous_free_format)
        return nullptr;
    if (this->info.layer != 3)                                            /* 159 */
        this->start = this->frame;
    else if (this->frame - this->start > MAX_RESERVOIR)                      /* 160 */
        this->start = this->frame - MAX_RESERVOIR;
    while (this->frame + this->info.frame_size > this->finish)                  /* 162 */
        if (this->state & END_OF_INPUT) {
            if (this->info.layer == 3 && (this->frame + this->info.fixed_size < this->finish &&   /* 331 */
                                          !(this->options.flags & MP3_NO_PARTIAL_FRAME))
                    )
                return this->frame;
            else
                return nullptr;
        }
        else
            fill_input_buffer ();
    return this->frame;
}

void stream::fill_input_buffer ()
{                                                                    /* 157 */
    int size = this->finish - this->start;

    if (size > 0) {                                                    /* 158 */
        int distance = this->start - this->buffer;

        if (distance > 0) {
            memmove (this->buffer, this->start, size);
            this->start = this->buffer;
            if (this->frame != nullptr)
                this->frame = this->frame - distance;
            this->byte_pointer = this->byte_pointer - distance;
            this->finish = this->finish - distance;
            this->buffer_position = this->buffer_position + distance;
        }
    }
    size = this->input_read (this->info.id, this->myFile, this->finish, BUFFERSIZE - size);
    if (size <= 0)
        this->state = this->state | END_OF_INPUT;
    else
        this->finish = this->finish + size;
}

unsigned char * stream::synchronize (int (*tag_read)( int id, void *buffer, int count) )
{                                                                    /* 161 */
    if (this->bit_offset > 0) {
        this->byte_pointer++;
        this->bit_offset = 0;
    }
    do {
        while (this->byte_pointer + HEADER_SIZE > this->finish)
            if (this->state & END_OF_INPUT)
                return nullptr;
            else
                this->fill_input_buffer ();
        if ( (&this->info)->decode_header ( this->byte_pointer)) {
            this->frame = this->byte_pointer;
            {
                if (this->info.free_format) {                                   /* 164 */
                    int frame_size = HEADER_SIZE;                              /* 167 */

                    do {
                        if (++frame_size > MAX_FRAME)
                            break;
                        while (this->frame + frame_size + HEADER_SIZE > this->finish)  /* 169 */
                            if (this->state & END_OF_INPUT)
                                return nullptr;
                            else
                                this->fill_input_buffer ();
                        if (!(this->frame[frame_size] == 0xFF &&                    /* 168 */
                              (this->frame[frame_size + 1] & 0xFE) == (this->frame[1] & 0xFE) &&
                              (this->frame[frame_size + 2] & 0xFC) == (this->frame[2] & 0xFC))
                                )
                            continue;
                        {
                            mp3_info i = { 0 }
                            , *info = &i;                                          /* 171 */
                            if (!info->decode_header ( this->frame + frame_size))
                                continue;
                            this->info.frame_size = frame_size;
                            {
                                if (this->info.layer == 1)                              /*  91 */
                                    this->info.bit_rate =
                                            ((this->info.frame_size / 4 -
                                              this->info.padding) * this->info.sample_rate + 11) / 12;
                                else
                                    this->info.bit_rate =
                                            ((this->info.frame_size -
                                              this->info.padding) * this->info.sample_rate + 143) / 144;
                            }
                            if (this->info.layer == 3 && this->info.version != MP3_V1_0) /* 376 */
                                this->info.bit_rate =
                                        ((this->info.frame_size -
                                          this->info.padding) * this->info.sample_rate + 71) / 72;
                            if (this->options.flags & MP3_SYNC_3) {
                                info->bit_rate = this->info.bit_rate;
                                {
                                    if (info->layer == 1)                              /*  90 */
                                        info->frame_size =
                                                4 * (info->padding +
                                                     (384 / (8 * 4)) * info->bit_rate /
                                                     info->sample_rate);
                                    else
                                        info->frame_size =
                                                info->padding +
                                                (1152 / 8) * info->bit_rate / info->sample_rate;
                                }
                                if (info->layer == 3 && info->version != MP3_V1_0)   /* 375 */
                                    info->frame_size =
                                            1 * (info->padding +
                                                 72 * info->bit_rate / info->sample_rate);
                                while (this->frame + this->info.frame_size + info->frame_size +
                                       HEADER_SIZE > this->finish)
                                    if (this->state & END_OF_INPUT)
                                        return this->frame;
                                    else
                                        this->fill_input_buffer ();
                                if (info->decode_header
                                        ( this->frame + this->info.frame_size + info->frame_size))
                                    return this->frame;
                                else
                                    break;
                            }
                            else
                                return this->frame;
                        }
                    } while (true);
                }
                else {
                    while (this->frame + this->info.frame_size > this->finish)          /* 162 */
                        if (this->state & END_OF_INPUT) {
                            if (this->info.layer == 3 && (this->frame + this->info.fixed_size < this->finish &&   /* 331 */
                                                          !(this->options.
                                                                  flags & MP3_NO_PARTIAL_FRAME))
                                    )
                                return this->frame;
                            else
                                return nullptr;
                        }
                        else
                            this->fill_input_buffer ();
                    if (this->options.flags & MP3_SYNC_1)
                        return this->frame;
                    else {
                        mp3_info i = { 0 };                                      /* 165 */
                        while (this->frame + this->info.frame_size + HEADER_SIZE > this->finish)
                            if (this->state & END_OF_INPUT)
                                return this->frame;
                            else
                                this->fill_input_buffer ();
                        if ((&i)->decode_header ( this->frame + this->info.frame_size)) {
                            if (this->options.flags & MP3_SYNC_3) {
                                while (this->frame + this->info.frame_size + i.frame_size +
                                       HEADER_SIZE > this->finish)
                                    if (this->state & END_OF_INPUT)
                                        return this->frame;
                                    else
                                        this->fill_input_buffer ();
                                if (i.free_format)
                                    return this->frame;
                                else
                                if ((&i)->decode_header
                                        ( this->frame + this->info.frame_size + i.frame_size))
                                    return this->frame;
                            }
                            else
                                return this->frame;
                        }
                    }
                }
                this->frame = nullptr;
                this->byte_pointer++;
            }
        }
        else {
            if (this->options.tag_handler != nullptr) {                          /* 172 */
                this->tag_size = 0;                                             /* 174 */
                this->options.tag_handler (this->info.id, tag_read);
                if (this->tag_size <= 0)
                    this->byte_pointer++;
                else {
                    int post = this->finish - this->byte_pointer - this->tag_size;      /* 179 */

                    int pre = this->byte_pointer - this->start;

                    if (post > 0) {
                        if (pre > 0)
                            memmove (this->start + this->tag_size, this->start, pre);
                        this->byte_pointer = this->byte_pointer + this->tag_size;
                        this->start = this->start + this->tag_size;
                        if (this->frame != nullptr)
                            this->frame = this->frame + this->tag_size;
                    }
                    else
                        this->finish = this->byte_pointer;
                }
            }
            else
                this->byte_pointer++;
        }
        if (this->byte_pointer - this->start > MAX_RESERVOIR)
            this->start = this->byte_pointer - MAX_RESERVOIR;
    } while (true);
    return nullptr;
}


//void stream::output_blocks ( mp3_sample * buffer, const int n    )
