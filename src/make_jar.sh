#!/bin/sh
rm -f ../bin/ApkDebug.jar
cd ../tmp
jar cmfv ../src/MANIFEST.MF ../bin/ApkDebug.jar jp
