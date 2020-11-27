# Loot logger external

This plugin will let you send loot drop information to an api endpoint of your choosing.

## Endpoint
The loot data will be sent out as a post request with the following structure as the body:

```
{
  "ItemName": string,
  "ItemId":int,
  "ItemQuantity": int,
  "EventName": string,
  "Username": string
}
```

An example of a valid endpoint in c# would be:

```
[HttpPost("add")]
public async Task AddDrop(GameDrop drop)
  {
      await BankHelper.AddItemToVirtualBank(drop.ItemId, drop.Username, DateTime.Now,drop.ItemQuantity); 
  }
```

Where the class GameDrop:

```
public class GameDrop
{
    public string ItemName { get; set; }
    public int ItemId { get; set; }
    public int ItemQuantity { get; set; }
    public string EventName { get; set; }
    public string Username { get; set; }
}
```
