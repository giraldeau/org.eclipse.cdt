ELF test files
===================

The ELF and DWARF parsers need input files for testing. This directory contains an executable and a library in C++. The script multibuild cross-compiles for many architectures to support. 

The list of spec is:
 - Intel i386
 - Intel x86\_64
 - ARM 32
 - PowerPC 32 (big endian)

Compile the sources on Ubuntu 15.10
-----------------------------------

Install required packages

    dpkg --add-architecture i386
    apt-get update
    sudo apt-get install build-essential qt5-qmake qtbase5-dev linux-libc-dev:i386 libc6-dev-i386 lib32stdc++-5-dev g++-powerpc-linux-gnu g++-arm-linux-gnueabi

Compile the project:

    cd org.eclipse.cdt.core.tests/resources/elf
    ./multibuild foo/foo.pro

The compiled files are under their build directory, namely ```build_{project}_{spec}/```. 

