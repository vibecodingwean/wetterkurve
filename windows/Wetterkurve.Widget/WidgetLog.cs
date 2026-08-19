namespace Wetterkurve.Widget;

static class WidgetLog
{
    static readonly string PathName = ResolvePath();

    static string ResolvePath()
    {
        try
        {
            var folder = Windows.Storage.ApplicationData.Current.LocalFolder.Path;
            return System.IO.Path.Combine(folder, "widget.log");
        }
        catch
        {
            var folder = System.IO.Path.Combine(
                Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
                "Wetterkurve");
            Directory.CreateDirectory(folder);
            return System.IO.Path.Combine(folder, "widget.log");
        }
    }

    public static void Write(string message)
    {
        try
        {
            File.AppendAllText(PathName, $"{DateTime.Now:s} {message}{Environment.NewLine}");
        }
        catch
        {
        }
    }
}
