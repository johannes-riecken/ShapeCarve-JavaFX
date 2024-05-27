package org.example.shapecarvejavafx;
import java.util.*;

@FunctionalInterface
interface IntTernaryOperator {
    int applyAsInt(int a, int b, int c);
}

public class Greedy {
    public record Mesh(List<List<Integer>> vertices, List<List<Integer>> faces) {}
    public static Mesh greedyMesh(int[] volume, int[] dims) {
        //Cache buffer internally
        var mask = new int[(4096)];
        IntTernaryOperator f = (int i, int j, int k) -> volume[i + dims[0] * (j + dims[1] * k)];

        //Sweep over 3-axes
        List<List<Integer>> vertices = new ArrayList<>(), faces = new ArrayList<>();
        for(var d=0; d<3; ++d) {
            int i, j, k, l, w, h
                    , u = (d+1)%3
                    , v = (d+2)%3;
            int[] x = new int[]{0,0,0}
                    , q = new int[]{0,0,0};
            if(mask.length < dims[u] * dims[v]) {
                mask = new int[(dims[u] * dims[v])];
            }
            q[d] = 1;
            for(x[d]=-1; x[d]<dims[d]; ) {
                //Compute mask
                var n = 0;
                for(x[v]=0; x[v]<dims[v]; ++x[v])
                    for(x[u]=0; x[u]<dims[u]; ++x[u], ++n) {
                        int a = (0    <= x[d]      ? f.applyAsInt(x[0],      x[1],      x[2])      : 0)
                                , b = (x[d] <  dims[d]-1 ? f.applyAsInt(x[0]+q[0], x[1]+q[1], x[2]+q[2]) : 0);
                        if((a != 0) == (b != 0) ) {
                            mask[n] = 0;
                        } else if(a != 0) {
                            mask[n] = a;
                        } else {
                            mask[n] = -b;
                        }
                    }
                //Increment x[d]
                ++x[d];
                //Generate mesh for mask using lexicographic ordering
                n = 0;
                for(j=0; j<dims[v]; ++j)
                    for(i=0; i<dims[u]; ) {
                        var c = mask[n];
                        if(c != 0) {
                            //Compute width
                            for(w=1; c == mask[n+w] && i+w<dims[u]; ++w) {
                                // doesn't need body
                            }
                            //Compute height (this is slightly awkward)
                            var done = false;
                            for(h=1; j+h<dims[v]; ++h) {
                                for(k=0; k<w; ++k) {
                                    if(c != mask[n+k+h*dims[u]]) {
                                        done = true;
                                        break;
                                    }
                                }
                                if(done) {
                                    break;
                                }
                            }
                            //Add quad
                            x[u] = i;  x[v] = j;
                            int[] du = new int[]{0,0,0}
                                    , dv = new int[]{0,0,0};
                            if(c > 0) {
                                dv[v] = h;
                                du[u] = w;
                            } else {
                                c = -c;
                                du[v] = h;
                                dv[u] = w;
                            }
                            var vertexCount = vertices.size();
                            vertices.add(List.of(x[0],             x[1],             x[2]            ));
                            vertices.add(List.of(x[0]+du[0],       x[1]+du[1],       x[2]+du[2]      ));
                            vertices.add(List.of(x[0]+du[0]+dv[0], x[1]+du[1]+dv[1], x[2]+du[2]+dv[2]));
                            vertices.add(List.of(x[0]      +dv[0], x[1]      +dv[1], x[2]      +dv[2]));
                            faces.add(List.of(vertexCount, vertexCount+1, vertexCount+2, vertexCount+3, c));

                            //Zero-out mask
                            for(l=0; l<h; ++l)
                                for(k=0; k<w; ++k) {
                                    mask[n+k+l*dims[u]] = 0;
                                }
                            //Increment counters and continue
                            i += w; n += w;
                        } else {
                            ++i;    ++n;
                        }
                    }
            }
        }
        return new Mesh(vertices, faces);
    }
}
