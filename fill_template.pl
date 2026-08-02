#!/usr/bin/perl -w
use v5.30;
use Data::Dumper;

open my $f, '<', 'snippet.py';
my $snippet = '';
while (<$f>) {
    $snippet .= "\t$_";
}

open my $f_out, '>', 'main.py';
while (<>) {
    if (/^\s*+\.\.\.$/) {
        $_ = $snippet;
    }
    print {$f_out} $_;
}
