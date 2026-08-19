using System.Runtime.InteropServices;
using Microsoft.Windows.Widgets.Providers;
using WinRT;

namespace Wetterkurve.Widget.Com;

static class Guids
{
    public const string IClassFactory = "00000001-0000-0000-C000-000000000046";
    public const string IUnknown = "00000000-0000-0000-C000-000000000046";
}

[ComImport]
[InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
[Guid(Guids.IClassFactory)]
interface IClassFactory
{
    [PreserveSig]
    int CreateInstance(IntPtr pUnkOuter, ref Guid riid, out IntPtr ppvObject);

    [PreserveSig]
    int LockServer(bool fLock);
}

static class ClassObject
{
    public static void Register(Guid clsid, object factory, out uint cookie)
    {
        [DllImport("ole32.dll")]
        static extern int CoRegisterClassObject(
            [MarshalAs(UnmanagedType.LPStruct)] Guid rclsid,
            [MarshalAs(UnmanagedType.IUnknown)] object pUnk,
            uint dwClsContext,
            uint flags,
            out uint lpdwRegister);

        var result = CoRegisterClassObject(clsid, factory, 0x4, 0x1, out cookie);
        if (result != 0)
            Marshal.ThrowExceptionForHR(result);
    }

    public static int Revoke(uint cookie)
    {
        [DllImport("ole32.dll")]
        static extern int CoRevokeClassObject(uint dwRegister);

        return CoRevokeClassObject(cookie);
    }
}

[ComVisible(true)]
sealed class WidgetProviderFactory<T> : IClassFactory
    where T : IWidgetProvider, new()
{
    const int ClassENoAggregation = unchecked((int)0x80040110);

    public int CreateInstance(IntPtr pUnkOuter, ref Guid riid, out IntPtr ppvObject)
    {
        ppvObject = IntPtr.Zero;
        if (pUnkOuter != IntPtr.Zero)
            Marshal.ThrowExceptionForHR(ClassENoAggregation);

        // The Widgets host may QueryInterface for IWidgetProvider2 or
        // IWidgetResourceProvider. Hand out the WinRT inspectable for any
        // requested interface so those queries succeed.
        ppvObject = MarshalInspectable<IWidgetProvider>.FromManaged(new T());
        return 0;
    }

    int IClassFactory.LockServer(bool fLock) => 0;
}

sealed class RegistrationManager<T> : IDisposable
    where T : IWidgetProvider, new()
{
    readonly uint _cookie;
    readonly ManualResetEvent _disposed = new(false);
    bool _disposedValue;

    RegistrationManager(uint cookie) => _cookie = cookie;

    public static RegistrationManager<T> RegisterProvider()
    {
        ClassObject.Register(typeof(T).GUID, new WidgetProviderFactory<T>(), out var cookie);
        return new RegistrationManager<T>(cookie);
    }

    public ManualResetEvent GetDisposedEvent() => _disposed;

    public void Dispose()
    {
        if (_disposedValue)
            return;
        ClassObject.Revoke(_cookie);
        _disposedValue = true;
        _disposed.Set();
    }
}
