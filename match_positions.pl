#!/usr/bin/perl -w
use v5.30;
use Data::Dumper;
use autodie;

open my $f, '<', 'src/main/java/org/example/shapecarvejavafx/ShapeCarver.java';
print '[';
while (<$f>) {
    while (/\[(?:[^\[\]]++|(?0))*+\]/g) {
        my ($line, $col, $len) = ($., $-[0] + 1, $+[0] - $-[0]);
        print "[$line, $col, $len], ";
    }
}
say ']';
