from std.python import Python, PythonObject
from std.sys import exit
import std.sys as sys

def main() raises -> None:
    var np = Python.import_module("numpy")
    var json = Python.import_module("json")
    var sysPy = Python.import_module("sys")
    var builtins = Python.import_module("builtins")


    var X, Y, Z = 0, 1, 2
    var argv = sys.argv()
    if len(argv) != 2:
        print("Usage: ./main.py <test_case_idx>", file=sys.stderr)
        exit(1)
    var test_case_idx: Int
    var j: PythonObject
    var f = builtins.open('test_cases.json', 'r')
    j = json.load(f)
    test_case_idx = Int(argv[1])
    var views = np.array(j[test_case_idx]['views'])

    var proj_to_view = [Z, Y, X]
    var N = views.shape[-1]
    var volume_dims = builtins.tuple([N, N, N])
    var tmp = Python.evaluate('(3, 2)')
    var skip = np.zeros(tmp, np.bool)
    var mask_color = 0
    var volume = np.full(volume_dims, -1, dtype=np.int32)

    var depths = np.empty(views.shape, np.int32)
    # --- Initial pass: compute depth bounds and carve out empty space ---
    for proj_axis in [X, Y, Z]:
        var vax = proj_to_view[proj_axis]
        for side_idx in range(2):
            var far = N - 1 if side_idx == 0 else 0
            var near = N - 1 if side_idx == 1 else 0
            depths[vax, side_idx] = np.where(
                (views[vax, side_idx] == mask_color) & ~skip[vax, side_idx],
                far, near,
            )
        var start = depths[vax, 1]
        var end   = depths[vax, 0]
        var all_depths = np.arange(N)
        var main_module = Python.import_module('__main__')
        main_module.start = start
        main_module.end = end
        main_module.all_depths = all_depths
        var mask = (all_depths >= Python.evaluate('start[:, :, None]')) & (all_depths <= Python.evaluate('end[:, :, None]')) if proj_axis == X else (Python.evaluate('all_depths[None, :, None]') >= Python.evaluate('start[:, None, :]')) & (Python.evaluate('all_depths[None, :, None]') <= Python.evaluate('end[:, None, :]')) if proj_axis == Y else (Python.evaluate('all_depths[:, None, None]') >= Python.evaluate('start[None, :, :]')) & (Python.evaluate('all_depths[:, None, None]') <= Python.evaluate('end[None, :, :]'))
        volume[mask] = mask_color

    # --- Refinement loop ---
    var voxels_removed = Python.evaluate('1')
    var cond = voxels_removed > 0
    var nn_tuple = builtins.tuple([N, N])
    var grid = np.indices(nn_tuple)

    while cond:
        voxels_removed = 0
        for proj_axis in [X, Y, Z]:
            var vax = proj_to_view[proj_axis]
            for side_idx in range(2):
                if skip[vax, side_idx]:
                    continue

                var sweep_dir = 1 if side_idx == 0 else -1
                var view = views[vax, side_idx]
                var d = depths[vax, side_idx].copy()
                var nn_tuple = builtins.tuple([N, N])
                var active = np.ones(nn_tuple, dtype=np.bool)
                var last_d = np.full(nn_tuple, N - 1 if side_idx == 0 else 0, dtype=np.int32)

                while np.any(active):
                    var valid = active & (d >= 0) & (d < N)
                    if not np.any(valid):
                        break
                    active &= valid

                    var v_sub = grid[0][valid]
                    var u_sub = grid[1][valid]
                    var d_sub = d[valid]

                    var z_sub: PythonObject
                    var y_sub: PythonObject
                    var x_sub: PythonObject
                    if proj_axis == X:
                        z_sub, y_sub, x_sub = v_sub, u_sub, d_sub
                    elif proj_axis == Y:
                        z_sub, y_sub, x_sub = v_sub, d_sub, u_sub
                    else:  # Z
                        z_sub, y_sub, x_sub = d_sub, v_sub, u_sub

                    var vol_colors = volume[z_sub, y_sub, x_sub]
                    var already_masked = (vol_colors == mask_color)

                    var test_mask = ~already_masked
                    var is_consistent_sub = np.zeros(len(v_sub), dtype=np.bool)

                    if np.any(test_mask):
                        var z_test = z_sub[test_mask]
                        var y_test = y_sub[test_mask]
                        var x_test = x_sub[test_mask]
                        var v_test = v_sub[test_mask]
                        var u_test = u_sub[test_mask]

                        var cand_color = view[v_test, u_test]
                        volume[z_test, y_test, x_test] = cand_color

                        var consistent_test = np.ones(len(z_test), dtype=np.bool)

                        for check_axis in [X, Y, Z]:
                            var check_vax = proj_to_view[check_axis]
                            var check_v: PythonObject
                            var check_u: PythonObject
                            var check_coord: PythonObject
                            if check_axis == X:
                                check_v, check_u, check_coord = z_test, y_test, x_test
                            elif check_axis == Y:
                                check_v, check_u, check_coord = z_test, x_test, y_test
                            else:  # Z
                                check_v, check_u, check_coord = y_test, x_test, z_test

                            for check_side in [0, 1]:
                                if skip[check_vax, check_side]:
                                    continue

                                var check_view_color = views[check_vax, check_side, check_v, check_u]
                                var check_view_depth = depths[check_vax, check_side, check_v, check_u]

                                var is_occluded = check_view_depth <= check_coord if check_side == 1 else check_coord <= check_view_depth

                                var inconsistent = is_occluded & (check_view_color != cand_color)
                                consistent_test &= ~inconsistent

                        var inconsistent_test = ~consistent_test
                        var num_inconsistent = np.sum(inconsistent_test)
                        if num_inconsistent > 0:
                            voxels_removed += num_inconsistent
                            volume[z_test[inconsistent_test], y_test[inconsistent_test], x_test[inconsistent_test]] = mask_color

                        is_consistent_sub[test_mask] = consistent_test

                    var found_consistent = is_consistent_sub
                    if np.any(found_consistent):
                        var valid_v = v_sub[found_consistent]
                        var valid_u = u_sub[found_consistent]
                        last_d[valid_v, valid_u] = d_sub[found_consistent]
                        active[valid_v, valid_u] = False

                    d[valid] += sweep_dir

                depths[vax, side_idx] = last_d

        cond = voxels_removed > 0

    volume[volume < 0] = 16711935
    print(builtins.str(volume.tolist()).replace(" ", ""))
