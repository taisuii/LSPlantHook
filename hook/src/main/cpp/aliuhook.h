//
// Created by ven on 24/03/2022.
//

#ifndef SONGKIT_SONGKIT_H
#define SONGKIT_SONGKIT_H

#include "elf_img.h"

void *InlineHooker(void *, void *);

bool InlineUnhooker(void *);

class NativeCore {
public:
    static pine::ElfImg elf_img;
    static int android_version;

    static void init(int version);
};

#endif //SONGKIT_SONGKIT_H
