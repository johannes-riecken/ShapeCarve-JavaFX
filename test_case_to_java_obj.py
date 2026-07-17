# Standard Java doesn't have JSON support
import javaobj.v3 as javaobj
import json
import numpy as np

f = open('test_cases.json')
jj = json.load(f)

a = np.array(jj[0]['views'])

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

