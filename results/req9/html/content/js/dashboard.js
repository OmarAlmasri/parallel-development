/*
   Licensed to the Apache Software Foundation (ASF) under one or more
   contributor license agreements.  See the NOTICE file distributed with
   this work for additional information regarding copyright ownership.
   The ASF licenses this file to You under the Apache License, Version 2.0
   (the "License"); you may not use this file except in compliance with
   the License.  You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
*/
var showControllersOnly = false;
var seriesFilter = "";
var filtersOnlySampleSeries = true;

/*
 * Add header in statistics table to group metrics by category
 * format
 *
 */
function summaryTableHeader(header) {
    var newRow = header.insertRow(-1);
    newRow.className = "tablesorter-no-sort";
    var cell = document.createElement('th');
    cell.setAttribute("data-sorter", false);
    cell.colSpan = 1;
    cell.innerHTML = "Requests";
    newRow.appendChild(cell);

    cell = document.createElement('th');
    cell.setAttribute("data-sorter", false);
    cell.colSpan = 3;
    cell.innerHTML = "Executions";
    newRow.appendChild(cell);

    cell = document.createElement('th');
    cell.setAttribute("data-sorter", false);
    cell.colSpan = 7;
    cell.innerHTML = "Response Times (ms)";
    newRow.appendChild(cell);

    cell = document.createElement('th');
    cell.setAttribute("data-sorter", false);
    cell.colSpan = 1;
    cell.innerHTML = "Throughput";
    newRow.appendChild(cell);

    cell = document.createElement('th');
    cell.setAttribute("data-sorter", false);
    cell.colSpan = 2;
    cell.innerHTML = "Network (KB/sec)";
    newRow.appendChild(cell);
}

/*
 * Populates the table identified by id parameter with the specified data and
 * format
 *
 */
function createTable(table, info, formatter, defaultSorts, seriesIndex, headerCreator) {
    var tableRef = table[0];

    // Create header and populate it with data.titles array
    var header = tableRef.createTHead();

    // Call callback is available
    if(headerCreator) {
        headerCreator(header);
    }

    var newRow = header.insertRow(-1);
    for (var index = 0; index < info.titles.length; index++) {
        var cell = document.createElement('th');
        cell.innerHTML = info.titles[index];
        newRow.appendChild(cell);
    }

    var tBody;

    // Create overall body if defined
    if(info.overall){
        tBody = document.createElement('tbody');
        tBody.className = "tablesorter-no-sort";
        tableRef.appendChild(tBody);
        var newRow = tBody.insertRow(-1);
        var data = info.overall.data;
        for(var index=0;index < data.length; index++){
            var cell = newRow.insertCell(-1);
            cell.innerHTML = formatter ? formatter(index, data[index]): data[index];
        }
    }

    // Create regular body
    tBody = document.createElement('tbody');
    tableRef.appendChild(tBody);

    var regexp;
    if(seriesFilter) {
        regexp = new RegExp(seriesFilter, 'i');
    }
    // Populate body with data.items array
    for(var index=0; index < info.items.length; index++){
        var item = info.items[index];
        if((!regexp || filtersOnlySampleSeries && !info.supportsControllersDiscrimination || regexp.test(item.data[seriesIndex]))
                &&
                (!showControllersOnly || !info.supportsControllersDiscrimination || item.isController)){
            if(item.data.length > 0) {
                var newRow = tBody.insertRow(-1);
                for(var col=0; col < item.data.length; col++){
                    var cell = newRow.insertCell(-1);
                    cell.innerHTML = formatter ? formatter(col, item.data[col]) : item.data[col];
                }
            }
        }
    }

    // Add support of columns sort
    table.tablesorter({sortList : defaultSorts});
}

$(document).ready(function() {

    // Customize table sorter default options
    $.extend( $.tablesorter.defaults, {
        theme: 'blue',
        cssInfoBlock: "tablesorter-no-sort",
        widthFixed: true,
        widgets: ['zebra']
    });

    var data = {"OkPercent": 100.0, "KoPercent": 0.0};
    var dataset = [
        {
            "label" : "FAIL",
            "data" : data.KoPercent,
            "color" : "#FF6347"
        },
        {
            "label" : "PASS",
            "data" : data.OkPercent,
            "color" : "#9ACD32"
        }];
    $.plot($("#flot-requests-summary"), dataset, {
        series : {
            pie : {
                show : true,
                radius : 1,
                label : {
                    show : true,
                    radius : 3 / 4,
                    formatter : function(label, series) {
                        return '<div style="font-size:8pt;text-align:center;padding:2px;color:white;">'
                            + label
                            + '<br/>'
                            + Math.round10(series.percent, -2)
                            + '%</div>';
                    },
                    background : {
                        opacity : 0.5,
                        color : '#000'
                    }
                }
            }
        },
        legend : {
            show : true
        }
    });

    // Creates APDEX table
    createTable($("#apdexTable"), {"supportsControllersDiscrimination": true, "overall": {"data": [0.9388235294117647, 500, 1500, "Total"], "isController": false}, "titles": ["Apdex", "T (Toleration threshold)", "F (Frustration threshold)", "Label"], "items": [{"data": [0.97, 500, 1500, "GET /api/cart"], "isController": false}, {"data": [1.0, 500, 1500, "GET /api/categories"], "isController": false}, {"data": [0.88, 500, 1500, "POST /api/auth/login"], "isController": false}, {"data": [0.895, 500, 1500, "POST /api/cart/items - Checkout Setup"], "isController": false}, {"data": [1.0, 500, 1500, "GET /actuator/health"], "isController": false}, {"data": [0.945, 500, 1500, "GET /api/products/{id}"], "isController": false}, {"data": [0.98, 500, 1500, "PUT /api/users/me"], "isController": false}, {"data": [0.98, 500, 1500, "GET /api/products"], "isController": false}, {"data": [0.97, 500, 1500, "GET /api/users/me"], "isController": false}, {"data": [0.95, 500, 1500, "GET /api/orders/me"], "isController": false}, {"data": [0.885, 500, 1500, "DELETE /api/cart/items/{productId}"], "isController": false}, {"data": [0.98, 500, 1500, "GET /api/transactions/me"], "isController": false}, {"data": [0.845, 500, 1500, "Checkout SUCCESS"], "isController": false}, {"data": [0.86, 500, 1500, "POST /api/users/me/deposit"], "isController": false}, {"data": [0.965, 500, 1500, "GET /api/products?categoryId"], "isController": false}, {"data": [0.885, 500, 1500, "POST /api/cart/items"], "isController": false}, {"data": [0.97, 500, 1500, "PUT /api/cart/items/{productId}"], "isController": false}]}, function(index, item){
        switch(index){
            case 0:
                item = item.toFixed(3);
                break;
            case 1:
            case 2:
                item = formatDuration(item);
                break;
        }
        return item;
    }, [[0, 0]], 3);

    // Create statistics table
    createTable($("#statisticsTable"), {"supportsControllersDiscrimination": true, "overall": {"data": ["Total", 1700, 0, 0.0, 244.91882352941218, 7, 1308, 177.0, 554.9000000000001, 730.8499999999995, 987.7500000000002, 176.91747320220628, 370.5678148415548, 64.9506037633989], "isController": false}, "titles": ["Label", "#Samples", "FAIL", "Error %", "Average", "Min", "Max", "Median", "90th pct", "95th pct", "99th pct", "Transactions/s", "Received", "Sent"], "items": [{"data": ["GET /api/cart", 100, 0, 0.0, 203.63999999999993, 37, 619, 169.5, 419.2000000000002, 520.4499999999998, 618.6499999999999, 16.249593760155996, 6.93337158758531, 5.900475046717582], "isController": false}, {"data": ["GET /api/categories", 100, 0, 0.0, 126.42, 12, 466, 81.5, 305.9, 409.09999999999957, 465.71999999999986, 20.938023450586265, 11.000641226968174, 7.725599023764657], "isController": false}, {"data": ["POST /api/auth/login", 100, 0, 0.0, 345.65999999999997, 95, 1249, 274.5, 713.6, 788.4999999999999, 1248.85, 22.43158366980709, 14.253694201435621, 5.715672667115298], "isController": false}, {"data": ["POST /api/cart/items - Checkout Setup", 100, 0, 0.0, 352.09, 81, 1079, 257.5, 746.3000000000001, 962.95, 1078.84, 16.49892756970797, 8.793412803167794, 6.892008074162679], "isController": false}, {"data": ["GET /actuator/health", 100, 0, 0.0, 49.50999999999999, 7, 278, 28.0, 135.9, 172.34999999999985, 277.1999999999996, 22.696323195642304, 14.916626475261008, 4.078245574216977], "isController": false}, {"data": ["GET /api/products/{id}", 100, 0, 0.0, 221.19999999999996, 16, 854, 154.5, 520.2, 548.55, 852.079999999999, 17.774617845716318, 10.825158860646996, 6.574352059633842], "isController": false}, {"data": ["PUT /api/users/me", 100, 0, 0.0, 178.84999999999988, 19, 1022, 141.0, 353.4000000000001, 486.6999999999997, 1019.4999999999987, 15.234613040828764, 7.971094702163315, 6.259729918875686], "isController": false}, {"data": ["GET /api/products", 100, 0, 0.0, 156.85999999999999, 14, 591, 100.5, 389.30000000000007, 465.1499999999998, 590.7699999999999, 20.3210729526519, 458.1540686598252, 7.458270359174964], "isController": false}, {"data": ["GET /api/users/me", 100, 0, 0.0, 184.70000000000007, 19, 848, 149.0, 360.70000000000005, 551.4499999999994, 846.9499999999995, 17.20282126268708, 9.000905836057115, 6.313804995269225], "isController": false}, {"data": ["GET /api/orders/me", 100, 0, 0.0, 231.67000000000004, 38, 898, 165.0, 529.7000000000002, 596.9999999999998, 895.9299999999989, 16.181229773462782, 10.263229419498382, 5.9546609526699035], "isController": false}, {"data": ["DELETE /api/cart/items/{productId}", 100, 0, 0.0, 355.87000000000006, 95, 1231, 234.5, 844.8000000000004, 937.4999999999999, 1230.98, 15.827793605571385, 6.753398029439697, 6.2252381588319095], "isController": false}, {"data": ["GET /api/transactions/me", 100, 0, 0.0, 180.64999999999998, 24, 881, 134.5, 399.5000000000001, 493.7499999999997, 879.8999999999994, 16.523463317911435, 10.57630741903503, 6.177419396480503], "isController": false}, {"data": ["Checkout SUCCESS", 100, 0, 0.0, 397.00000000000006, 75, 1308, 295.0, 837.4000000000001, 947.6999999999997, 1306.3499999999992, 17.1939477303989, 8.126826856946355, 6.730322332788859], "isController": false}, {"data": ["POST /api/users/me/deposit", 100, 0, 0.0, 407.66, 95, 1000, 343.5, 839.8000000000001, 923.8499999999997, 999.5899999999998, 14.788524105294291, 7.737690864389235, 6.106476218204674], "isController": false}, {"data": ["GET /api/products?categoryId", 100, 0, 0.0, 186.89000000000001, 13, 886, 117.5, 422.8, 562.6499999999999, 883.6099999999988, 18.23486506199854, 88.73256291028446, 6.924084552789934], "isController": false}, {"data": ["POST /api/cart/items", 100, 0, 0.0, 376.03000000000014, 95, 1252, 290.5, 779.3000000000002, 930.9499999999994, 1249.709999999999, 16.126431220770844, 8.594883889695211, 6.736407182309305], "isController": false}, {"data": ["PUT /api/cart/items/{productId}", 100, 0, 0.0, 208.92000000000007, 34, 905, 164.5, 450.6000000000001, 655.7999999999997, 904.6599999999999, 16.14987080103359, 8.607376453488373, 6.541170815164729], "isController": false}]}, function(index, item){
        switch(index){
            // Errors pct
            case 3:
                item = item.toFixed(2) + '%';
                break;
            // Mean
            case 4:
            // Mean
            case 7:
            // Median
            case 8:
            // Percentile 1
            case 9:
            // Percentile 2
            case 10:
            // Percentile 3
            case 11:
            // Throughput
            case 12:
            // Kbytes/s
            case 13:
            // Sent Kbytes/s
                item = item.toFixed(2);
                break;
        }
        return item;
    }, [[0, 0]], 0, summaryTableHeader);

    // Create error table
    createTable($("#errorsTable"), {"supportsControllersDiscrimination": false, "titles": ["Type of error", "Number of errors", "% in errors", "% in all samples"], "items": []}, function(index, item){
        switch(index){
            case 2:
            case 3:
                item = item.toFixed(2) + '%';
                break;
        }
        return item;
    }, [[1, 1]]);

        // Create top5 errors by sampler
    createTable($("#top5ErrorsBySamplerTable"), {"supportsControllersDiscrimination": false, "overall": {"data": ["Total", 1700, 0, "", "", "", "", "", "", "", "", "", ""], "isController": false}, "titles": ["Sample", "#Samples", "#Errors", "Error", "#Errors", "Error", "#Errors", "Error", "#Errors", "Error", "#Errors", "Error", "#Errors"], "items": [{"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}, {"data": [], "isController": false}]}, function(index, item){
        return item;
    }, [[0, 0]], 0);

});
