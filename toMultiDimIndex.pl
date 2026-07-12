#!/usr/bin/perl -w
use v5.30;
use Data::Dumper;

while (<>) {
    s/\.get(\(([^\(\)]++|(?1))*+\))/[$2]/g;
    while (/\[((?:[^\[\]]++|(?R))*+)\]/g) {
        my $offset = $-[1];
        my $length = $+[1] - $-[1];
        next if $1 =~ /^\w++$/;
        my $src = $1;
        my $dst = `runhaskell ToMultiDimIndex.hs <<<"$src"`;
        chomp $dst;
        # TODO: Doesn't work, because it would reset the regex position
        # substr $_, $offset, $length, $dst;
        print;
    }
}
