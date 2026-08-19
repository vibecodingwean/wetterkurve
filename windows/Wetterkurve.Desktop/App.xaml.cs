using System.IO;
using System.Windows;
using System.Windows.Threading;

namespace Wetterkurve.Desktop;

public partial class App : System.Windows.Application
{
    void App_OnStartup(object sender, StartupEventArgs e)
    {
        DispatcherUnhandledException += (_, args) =>
        {
            WriteCrash(args.Exception);
            MessageBox.Show(args.Exception.Message, "Wetterkurve");
            args.Handled = true;
            Shutdown();
        };
        AppDomain.CurrentDomain.UnhandledException += (_, args) =>
        {
            if (args.ExceptionObject is Exception exception)
            {
                WriteCrash(exception);
                MessageBox.Show(exception.Message, "Wetterkurve");
            }
        };

        try
        {
            WriteLog("starting");
            var window = new MainWindow();
            MainWindow = window;
            window.Show();
            window.Activate();
            WriteLog("window shown");
        }
        catch (Exception exception)
        {
            WriteCrash(exception);
            MessageBox.Show(exception.ToString(), "Wetterkurve");
            Shutdown();
        }
    }

    static void WriteCrash(Exception exception) => WriteLog(exception.ToString());

    static void WriteLog(string message)
    {
        try
        {
            var directory = Path.Combine(
                Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
                "Wetterkurve");
            Directory.CreateDirectory(directory);
            File.AppendAllText(
                Path.Combine(directory, "start.log"),
                $"{DateTime.Now:s} {message}{Environment.NewLine}");
        }
        catch
        {
        }
    }
}
