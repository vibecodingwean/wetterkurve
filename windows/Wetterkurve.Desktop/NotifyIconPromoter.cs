using Microsoft.Win32;

namespace Wetterkurve.Desktop;

static class NotifyIconPromoter
{
    const string Root = @"Control Panel\NotifyIconSettings";

    public static bool Promote(string executablePath)
    {
        var promoted = false;
        try
        {
            using var key = Registry.CurrentUser.OpenSubKey(Root, writable: true);
            if (key is null)
                return false;

            foreach (var name in key.GetSubKeyNames())
            {
                using var sub = key.OpenSubKey(name, writable: true);
                if (sub is null)
                    continue;
                if (sub.GetValue("ExecutablePath") is not string path)
                    continue;
                if (!path.Contains("Wetterkurve.exe", StringComparison.OrdinalIgnoreCase) &&
                    !path.Equals(executablePath, StringComparison.OrdinalIgnoreCase))
                    continue;
                sub.SetValue("IsPromoted", 1, RegistryValueKind.DWord);
                promoted = true;
            }
        }
        catch
        {
        }
        return promoted;
    }
}
