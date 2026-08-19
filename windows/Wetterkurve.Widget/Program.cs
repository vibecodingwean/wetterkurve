using Wetterkurve.Widget.Com;

namespace Wetterkurve.Widget;

static class Program
{
    [MTAThread]
    static void Main(string[] args)
    {
        WidgetLog.Write($"Main args=[{string.Join(' ', args)}]");
        WinRT.ComWrappersSupport.InitializeComWrappers();
        using var manager = RegistrationManager<WidgetProvider>.RegisterProvider();
        WidgetLog.Write("COM server registered");
        using var disposed = manager.GetDisposedEvent();
        disposed.WaitOne();
        WidgetLog.Write("COM server exiting");
    }
}
