package com.fo.addon.utils;

/**
 * 缓动曲线（渲染动画用），完整移植自 Hachimi V2 反编译源码
 * {@code pw.hachimi.client.model.render.Easing}（数学公式逐个对齐，枚举名中文化）。
 *
 * <p>纯数学，无 MC 依赖，可单测。默认值「圆弧缓出」对齐上游 CIRC_OUT。</p>
 */
public enum Easing {
    线性 {
        public double apply(double t) { return t; }
    },
    正弦缓入 {
        public double apply(double t) { return 1.0 - Math.cos(t * Math.PI / 2.0); }
    },
    正弦缓出 {
        public double apply(double t) { return Math.sin(t * Math.PI / 2.0); }
    },
    正弦缓入出 {
        public double apply(double t) { return -(Math.cos(Math.PI * t) - 1.0) / 2.0; }
    },
    立方缓入 {
        public double apply(double t) { return Math.pow(t, 3.0); }
    },
    立方缓出 {
        public double apply(double t) { return 1.0 - Math.pow(1.0 - t, 3.0); }
    },
    立方缓入出 {
        public double apply(double t) {
            return t < 0.5 ? 4.0 * Math.pow(t, 3.0) : 1.0 - Math.pow(-2.0 * t + 2.0, 3.0) / 2.0;
        }
    },
    平方缓入 {
        public double apply(double t) { return Math.pow(t, 2.0); }
    },
    平方缓出 {
        public double apply(double t) { return 1.0 - (1.0 - t) * (1.0 - t); }
    },
    平方缓入出 {
        public double apply(double t) {
            return t < 0.5 ? 8.0 * Math.pow(t, 4.0) : 1.0 - Math.pow(-2.0 * t + 2.0, 4.0) / 2.0;
        }
    },
    四次缓入 {
        public double apply(double t) { return Math.pow(t, 4.0); }
    },
    四次缓出 {
        public double apply(double t) { return 1.0 - Math.pow(1.0 - t, 4.0); }
    },
    四次缓入出 {
        public double apply(double t) {
            return t < 0.5 ? 8.0 * Math.pow(t, 4.0) : 1.0 - Math.pow(-2.0 * t + 2.0, 4.0) / 2.0;
        }
    },
    五次缓入 {
        public double apply(double t) { return Math.pow(t, 5.0); }
    },
    五次缓出 {
        public double apply(double t) { return 1.0 - Math.pow(1.0 - t, 5.0); }
    },
    五次缓入出 {
        public double apply(double t) {
            return t < 0.5 ? 16.0 * Math.pow(t, 5.0) : 1.0 - Math.pow(-2.0 * t + 2.0, 5.0) / 2.0;
        }
    },
    圆弧缓入 {
        public double apply(double t) { return 1.0 - Math.sqrt(1.0 - Math.pow(t, 2.0)); }
    },
    圆弧缓出 {
        public double apply(double t) { return Math.sqrt(1.0 - Math.pow(t - 1.0, 2.0)); }
    },
    圆弧缓入出 {
        public double apply(double t) {
            if (t < 0.5) {
                return (1.0 - Math.sqrt(1.0 - Math.pow(2.0 * t, 2.0))) / 2.0;
            }
            return (Math.sqrt(1.0 - Math.pow(-2.0 * t + 2.0, 2.0)) + 1.0) / 2.0;
        }
    },
    指数缓入出 {
        public double apply(double t) {
            if (t == 0.0) return 0.0;
            if (t == 1.0) return 1.0;
            if (t < 0.5) return Math.pow(2.0, 20.0 * t - 10.0) / 2.0;
            return (2.0 - Math.pow(2.0, -20.0 * t + 10.0)) / 2.0;
        }
    },
    弹性缓入 {
        public double apply(double t) {
            if (t == 0.0) return 0.0;
            if (t == 1.0) return 1.0;
            return -Math.pow(2.0, 10.0 * t - 10.0) * Math.sin((t * 10.0 - 10.75) * (Math.PI * 2.0 / 3.0));
        }
    },
    弹性缓出 {
        public double apply(double t) {
            if (t == 0.0) return 0.0;
            if (t == 1.0) return 1.0;
            return Math.pow(2.0, -10.0 * t) * Math.sin((t * 10.0 - 0.75) * (Math.PI * 2.0 / 3.0)) + 1.0;
        }
    },
    弹性缓入出 {
        public double apply(double t) {
            if (t == 0.0) return 0.0;
            if (t == 1.0) return 1.0;
            double s = Math.sin((20.0 * t - 11.125) * (Math.PI * 4.0 / 9.0));
            if (t < 0.5) return -(Math.pow(2.0, 20.0 * t - 10.0) * s) / 2.0;
            return Math.pow(2.0, -20.0 * t + 10.0) * s / 2.0 + 1.0;
        }
    },
    回弹缓入 {
        public double apply(double t) { return 2.70158 * Math.pow(t, 3.0) - 1.70158 * t * t; }
    },
    回弹缓出 {
        public double apply(double t) {
            double c = 1.70158 + 1.0;
            return 1.0 + c * Math.pow(t - 1.0, 3.0) + 1.70158 * Math.pow(t - 1.0, 2.0);
        }
    },
    回弹缓入出 {
        public double apply(double t) {
            if (t < 0.5) {
                return Math.pow(2.0 * t, 2.0) * (7.189819 * t - 2.5949095) / 2.0;
            }
            return (Math.pow(2.0 * t - 2.0, 2.0) * (3.5949095 * (t * 2.0 - 2.0) + 2.5949095) + 2.0) / 2.0;
        }
    },
    弹跳缓入 {
        public double apply(double t) { return 1.0 - bounceOut(1.0 - t); }
    },
    弹跳缓出 {
        public double apply(double t) { return bounceOut(t); }
    },
    弹跳缓入出 {
        public double apply(double t) {
            if (t < 0.5) return (1.0 - bounceOut(1.0 - 2.0 * t)) / 2.0;
            return (1.0 + bounceOut(2.0 * t - 1.0)) / 2.0;
        }
    };

    /** 曲线函数：输入 [0,1] 进度，输出缓动后的值（弹性/回弹/弹跳可越出 [0,1]）。 */
    public abstract double apply(double t);

    /** 上游 {@code Easing.Yx}（弹跳曲线基准函数，数学对齐）。 */
    private static double bounceOut(double d) {
        if (d < 1.0 / 2.75) {
            return 7.5625 * d * d;
        } else if (d < 2.0 / 2.75) {
            double x = d - 1.5 / 2.75;
            return 7.5625 * x * x + 0.75;
        } else if (d < 2.5 / 2.75) {
            double x = d - 2.25 / 2.75;
            return 7.5625 * x * x + 0.9375;
        }
        double x = d - 2.625 / 2.75;
        return 7.5625 * x * x + 0.984375;
    }
}
