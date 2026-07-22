import ast
import numpy as np
import sys

if len(sys.argv) != 6:
    print('Usage: cuboid_projections.py <want_array> <dim0> <dim1> <dim2> <should_transpose>', file=sys.stderr)
    exit(1)

dims = [
    ast.literal_eval(sys.argv[2]),
    ast.literal_eval(sys.argv[3]),
    ast.literal_eval(sys.argv[4]),
    ]

should_transpose = ast.literal_eval(sys.argv[5])

want = np.array(
    ast.literal_eval(sys.argv[1]),
    dtype=np.dtype('>u4'),
    ).reshape(dims)

if should_transpose:
    want = want.transpose() # need to
# transpose, because original code expects (x, y, z), but I expect (z, y, x)
# But now I'm still puzzled, because view_left and view_front are transposed

# Assuming your array has shape (D, H, W)
# Create a boolean mask where elements are non-zero
def to_view(view_idx):
    axis = view_idx // 2
    mask = want != 0

    # np.argmax on a mask returns the index of the FIRST True along axis
    if view_idx % 2 == 1:
        # 1. Find the first non-zero in the flipped mask
        flipped_idx = np.argmax(np.flip(mask, axis=axis), axis=axis)
        # 2. CRUCIAL FIX: Map the index back to original coordinates
        first_nonzero_idx = want.shape[axis] - 1 - flipped_idx
    else:
        first_nonzero_idx = np.argmax(mask, axis=axis)

    # Expand dimensions to match the requirements of take_along_axis, then extract
    collapsed = np.take_along_axis(want, np.expand_dims(first_nonzero_idx, axis=axis), axis=axis)

    # Squeeze out the collapsed axis to get a final shape
    return collapsed.squeeze(axis=axis)

from PIL import Image
views = []
for i in range(6):
    # the 1:4 is because the alpha channel isn't used
    dims_map = {
            "x": dims[0],
            "y": dims[1],
            "z": dims[2],
            }
    del(dims_map["xyz"[i // 2]])
    view = to_view(i)
    three_channel_view = view.view(np.uint8).reshape(*dims_map.values(), 4)[:, :, 1:4]
    views.append(view.tolist())
    img = Image.fromarray(three_channel_view, 'RGB')
    img.save(f'img{i}.png')

print(views)

# 0 is front, 1 is top and 2 is left
# the three views correspond to (y, x), (z, x) and (z, y) views in MagicaVoxel
# that is also as expected
