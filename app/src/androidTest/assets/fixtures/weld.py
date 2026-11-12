def line_energy(power_kw, speed_m_per_min):
    """线能量 J/mm。中文注释用于搜索测试。"""
    return power_kw * 1000.0 / (speed_m_per_min * 1000.0 / 60.0)


def weld_depth(energy, k=0.42):
    return k * (energy ** 0.5)


# TODO: 校准 k 值
if __name__ == "__main__":
    print(line_energy(2.4, 3.2))
