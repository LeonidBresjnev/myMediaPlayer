/*
    Copyright 2005 Martin Ruckert

    ruckertm@acm.org

    This file is part of mp32pcm.

    mp32pcm is free software; you can redistribute it and/or modify
    it under the terms of the GNU Lesser General Public License as published by
    the Free Software Foundation; either version 2.1 of the License, or
    (at your option) any later version.

    mp32pcm is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Lesser General Public License for more details.

    You should have received a copy of the GNU Lesser General Public License
    along with mp32pcm; if not, write to the Free Software
    Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA

*/
#include <fstream>
#include <cstdlib>                                                  /*  10 */
#define MP3_MIN_BUFFER (2*1152)                                      /*  15 */
#define MP3_CONTINUE 0                                               /*  26 */
#define MP3_SKIP   0x0100
#define MP3_REPEAT 0x0200
#define MP3_MUTE   0x0400
#define MP3_REPAIR 0x0800
#define MP3_BREAK 0x1000                                             /*  27 */
#define MP3_ERROR -1                                                 /*  28 */
#define MP3_ERROR_NO_INPUT -2                                        /*  40 */
#define MP3_ERROR_TOO_MANY -3                                        /*  45 */
#define MP3_ERROR_MEMORY -4                                          /*  47 */
#define MP3_ERROR_NO_ID -5                                           /*  54 */
#define MP3_ERROR_NOT_OPEN -6
#define MP3_ERROR_DONE -7                                            /*  57 */
#define MP3_ERROR_NO_BUFFER -8
#define MP3_ERROR_NO_SIZE -9
#define SCALEFACTOR_ERROR 0x0010                                     /* 114 */
#define MP3_EQ_UNITGAIN 210                                          /* 446 */



#include "stream.h"


int mp3_open (std::ifstream*  ,
                     int (*input_read) (int id,std::ifstream*  , void *buffer, size_t )     /*  12 */
        , mp3_options *                  /*  18 */
);

int mp3_read (int id, mp3_sample * buffer, int size)                     /*  13 */;

int mp3_close (int id)                                   /*  16 */
;
