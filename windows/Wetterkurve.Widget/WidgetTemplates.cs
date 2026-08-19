namespace Wetterkurve.Widget;

static class WidgetTemplates
{
    public const string TextOnly = """
{
  "type": "AdaptiveCard",
  "version": "1.5",
  "$schema": "http://adaptivecards.io/schemas/adaptive-card.json",
  "body": [
    {
      "type": "ColumnSet",
      "columns": [
        {
          "type": "Column",
          "width": "stretch",
          "items": [
            {
              "type": "TextBlock",
              "text": "${title}",
              "size": "Large",
              "weight": "Bolder",
              "wrap": true
            },
            {
              "type": "TextBlock",
              "text": "${condition}",
              "spacing": "None",
              "wrap": true
            }
          ]
        },
        {
          "type": "Column",
          "width": "auto",
          "items": [
            {
              "type": "TextBlock",
              "text": "${temperature}",
              "size": "ExtraLarge",
              "weight": "Lighter"
            }
          ]
        }
      ]
    },
    {
      "type": "TextBlock",
      "text": "${stats}",
      "wrap": true,
      "spacing": "Medium"
    },
    {
      "type": "TextBlock",
      "text": "${status}",
      "size": "Small",
      "isSubtle": true,
      "spacing": "Small"
    }
  ],
  "actions": [
    {
      "type": "Action.Execute",
      "title": "${refreshLabel}",
      "verb": "refresh"
    }
  ]
}
""";

    public const string Display = """
{
  "type": "AdaptiveCard",
  "version": "1.5",
  "$schema": "http://adaptivecards.io/schemas/adaptive-card.json",
  "body": [
    {
      "type": "ColumnSet",
      "columns": [
        {
          "type": "Column",
          "width": "stretch",
          "items": [
            {
              "type": "TextBlock",
              "text": "${title}",
              "size": "Large",
              "weight": "Bolder",
              "wrap": true
            },
            {
              "type": "TextBlock",
              "text": "${condition}",
              "spacing": "None",
              "wrap": true
            }
          ]
        },
        {
          "type": "Column",
          "width": "auto",
          "items": [
            {
              "type": "TextBlock",
              "text": "${temperature}",
              "size": "ExtraLarge",
              "weight": "Lighter"
            }
          ]
        }
      ]
    },
    {
      "type": "TextBlock",
      "text": "${stats}",
      "wrap": true,
      "spacing": "Medium"
    },
    {
      "type": "Image",
      "url": "${chartUrl}",
      "altText": "${chartTitle}",
      "size": "Stretch",
      "spacing": "Small"
    },
    {
      "type": "TextBlock",
      "text": "${status}",
      "size": "Small",
      "isSubtle": true,
      "spacing": "Small"
    }
  ],
  "actions": [
    {
      "type": "Action.Execute",
      "title": "${refreshLabel}",
      "verb": "refresh"
    }
  ]
}
""";
}
