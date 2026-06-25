resource "azurerm_public_ip" "spot" {
  name                = "${var.name_prefix}-spot-pip"
  resource_group_name = var.resource_group_name
  location            = var.location
  allocation_method   = "Static"
  sku                 = "Standard"

  tags = var.tags
}

resource "azurerm_network_interface" "spot" {
  name                = "${var.name_prefix}-spot-nic"
  resource_group_name = var.resource_group_name
  location            = var.location

  ip_configuration {
    name                          = "internal"
    subnet_id                     = var.subnet_id
    private_ip_address_allocation = "Dynamic"
    public_ip_address_id          = azurerm_public_ip.spot.id
  }

  tags = var.tags
}

resource "azurerm_linux_virtual_machine" "forecast" {
  name                = "${var.name_prefix}-forecast-spot"
  resource_group_name = var.resource_group_name
  location            = var.location
  size                = var.instance_type
  zone                = var.availability_zone

  priority        = "Spot"
  eviction_policy = "Deallocate"
  max_bid_price   = try(tonumber(var.spot_price_max), null)

  admin_username = "azureuser"

  admin_ssh_key {
    username   = "azureuser"
    public_key = var.ssh_public_key
  }

  network_interface_ids = [azurerm_network_interface.spot.id]

  os_disk {
    caching              = "ReadWrite"
    storage_account_type = "Standard_LRS"
    disk_size_gb         = 30
  }

  source_image_reference {
    publisher = "Canonical"
    offer     = "0001-com-ubuntu-server-jammy"
    sku       = "22_04-lts"
    version   = "latest"
  }

  identity {
    type = "SystemAssigned"
  }

  tags = var.tags
}

resource "azurerm_virtual_machine_data_disk_attachment" "timescaledb" {
  managed_disk_id    = var.managed_disk_id
  virtual_machine_id = azurerm_linux_virtual_machine.forecast.id
  lun                = 0
  caching            = "ReadWrite"
}

resource "azurerm_virtual_machine_extension" "forecast" {
  name                 = "${var.name_prefix}-forecast-script"
  virtual_machine_id   = azurerm_linux_virtual_machine.forecast.id
  publisher            = "Microsoft.Azure.Extensions"
  type                 = "CustomScript"
  type_handler_version = "2.1"

  settings = jsonencode({
    fileUris         = []
    commandToExecute = "bash /opt/tickonomics/scripts/forecast-task.sh"
  })

  tags = var.tags
}
