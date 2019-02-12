//
// Created by Constantin on 2/6/2019.
//

#ifndef LIVE_VIDEO_10MS_ANDROID_PARSERAW_H
#define LIVE_VIDEO_10MS_ANDROID_PARSERAW_H

#include <cstdio>
#include "../NALU/NALU.hpp"

/*********************************************
 ** Parses a stream of raw h264 NALUs
**********************************************/

class ParseRAW {
public:
    ParseRAW(NALU_DATA_CALLBACK cb);
    void parseData(const uint8_t* data,const int data_length);
    void reset();
private:
    const NALU_DATA_CALLBACK cb;
    uint8_t nalu_data[NALU_MAXLEN];
    int nalu_data_position=4;
    int nalu_search_state=0;
};

#endif //LIVE_VIDEO_10MS_ANDROID_PARSERAW_H