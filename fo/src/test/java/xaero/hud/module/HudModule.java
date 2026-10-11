package xaero.hud.module;

/** 与真实 Xaero 的 {@code HudModule} 同形（真实版本是 final，这里不 final 无影响）。 */
public class HudModule<MS> {
    private Object currentSession;

    @SuppressWarnings("unchecked")
    public MS getCurrentSession() {
        return (MS) currentSession;
    }

    public void setCurrentSessionForTest(Object session) {
        this.currentSession = session;
    }
}
