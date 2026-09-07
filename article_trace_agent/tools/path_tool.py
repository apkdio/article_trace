import os


def get_project_root() -> str:
    """返回项目根目录（tools/ 的上一级）。"""
    return os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def get_abs_path(relative_path):
    """把相对项目根目录的路径转成绝对路径。"""
    return os.path.join(get_project_root(), relative_path)
