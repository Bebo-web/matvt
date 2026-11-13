package io.github.beboweb.shizukuinputtester;
interface IInputService {
    void destroy() = 16777114;
    String getIdentity() = 1;
    String injectTap(int x, int y, int source) = 2;
}