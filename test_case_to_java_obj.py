# Standard Java doesn't have JSON support
import javaobj.v3 as javaobj
import json
import numpy as np
import sys

if len(sys.argv) != 2:
    print('Usage: test_case_to_java_obj <test_case_index>', sys.stderr)
    exit(1)

f = open('test_cases.json')
jj = json.load(f)

test_case_index = int(sys.argv[1])

a = np.array(jj[test_case_index]['views'])

# transform to legacy conventions
# keep the front-back ordering intact
a = a.reshape(3, 2, a.shape[1], a.shape[2])
a = np.flip(a, axis=0)
a = a.reshape(6, a.shape[2], a.shape[3])
a[2] = a[2].transpose()
a[3] = a[3].transpose()
# # got (modern):
# (y, x)
# (z, x)
# (z, y)

# # want (legacy):
# (y,z)
# (z,x)
# (x,y)

# # what I see is just transposed (mario.png):
# (z,y)
# (x,z)
# (y,x)


# created with ArraySerializer.java
f = open('3dArray.ser', 'rb')
obj = javaobj.load(f, use_numpy_arrays=True)

list_of_2d_java_arrays = []
for i in range(a.shape[0]):
    list_of_1d_java_arrays = []
    for j in range(a.shape[1]):
        java_1d_array = javaobj.beans.JavaArray(
            handle=0,
            classdesc=javaobj.beans.JavaClassDesc(
                handle=0,
                name="[I",
                serial_version_uid=obj[0][0].classdesc.serial_version_uid,
                desc_flags=obj[0][0].classdesc.desc_flags,
            ),
            element_type=obj[0][0].element_type,
            data=list(a[i][j]),
        )
        list_of_1d_java_arrays.append(java_1d_array)

    java_2d_array = javaobj.beans.JavaArray(
        handle=0,
        classdesc=javaobj.beans.JavaClassDesc(
            handle=0,
            name="[[I",
            serial_version_uid=obj[0].classdesc.serial_version_uid,
            desc_flags=obj[0].classdesc.desc_flags,
        ),
        element_type=obj[0].element_type,
        data=list_of_1d_java_arrays,
    )
    list_of_2d_java_arrays.append(java_2d_array)

    root_3d_array = javaobj.beans.JavaArray(
        handle=0,
        classdesc=javaobj.beans.JavaClassDesc(
            handle=0,
            name="[[[I",
            serial_version_uid=obj.classdesc.serial_version_uid,
            desc_flags=obj.classdesc.desc_flags,
        ),
        element_type=obj.element_type,
        data=list_of_2d_java_arrays,
    )

with open('roundtrip.ser', 'wb') as f_out:
    javaobj.dump(f_out, root_3d_array)
