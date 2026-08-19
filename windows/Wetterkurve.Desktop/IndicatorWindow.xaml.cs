using System.Windows;
using System.Windows.Input;
using System.Windows.Media.Imaging;

namespace Wetterkurve.Desktop;

public partial class IndicatorWindow : Window
{
    readonly MainWindow _owner;

    public IndicatorWindow(MainWindow owner)
    {
        _owner = owner;
        InitializeComponent();
        Loaded += (_, _) => Place();
        MouseLeftButtonDown += OnMouseLeftButtonDown;
        MouseRightButtonUp += (_, _) => _owner.RestoreFromTray();
    }

    public void Update(string city, string temperature, string iconName)
    {
        CityLabel.Text = city;
        TemperatureLabel.Text = temperature;
        try
        {
            ConditionIcon.Source = new BitmapImage(new Uri(
                $"pack://application:,,,/Assets/icons/{iconName}.png"));
        }
        catch
        {
            ConditionIcon.Source = null;
        }
    }

    public void Place()
    {
        if (_owner.State.IndicatorLeft is { } left && _owner.State.IndicatorTop is { } top)
        {
            Left = left;
            Top = top;
            return;
        }

        var work = SystemParameters.WorkArea;
        UpdateLayout();
        Left = work.Right - ActualWidth - 16;
        Top = work.Bottom - ActualHeight - 10;
    }

    void OnMouseLeftButtonDown(object sender, MouseButtonEventArgs e)
    {
        var start = new Point(Left, Top);
        DragMove();
        if (Math.Abs(Left - start.X) + Math.Abs(Top - start.Y) < 4)
        {
            _owner.ToggleFromTray();
            return;
        }
        _owner.State.IndicatorLeft = Left;
        _owner.State.IndicatorTop = Top;
        _owner.Persist();
    }
}
