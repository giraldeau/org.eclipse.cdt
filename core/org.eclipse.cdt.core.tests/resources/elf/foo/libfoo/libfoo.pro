QT -= core gui

TARGET = foo
TEMPLATE = lib

exists(../custom.pri) {
    include(../custom.pri)
}

DEFINES += LIBFOO_LIBRARY

SOURCES += libfoo.cpp

HEADERS += libfoo.h

unix {
    target.path = /usr/lib
    INSTALLS += target
}
